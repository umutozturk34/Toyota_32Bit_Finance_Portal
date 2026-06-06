#!/bin/sh
set -e


# Finance Portal — OpenSearch Init Script
# Creates: ISM Policy, Index Templates, Index Patterns, 32 Visualizations, 10 Dashboards
# Idempotent: Skips if dashboards already exist, re-creates if missing

apk add --no-cache curl jq > /dev/null 2>&1

OS_URL="https://opensearch:9200"
DASH_URL="http://opensearch-dashboards:5601"
CREDS="${OPENSEARCH_USERNAME}:${OPENSEARCH_PASSWORD}"

os_api()  { curl -sk -u "$CREDS" "$@"; }
dash_api() { curl -sk -u "$CREDS" -H "osd-xsrf: true" "$@"; }

# ─────────────────────────── 1. Servislerin Hazır Olmasını Bekle ──────────────
echo "OpenSearch bekleniyor..."
until os_api "$OS_URL/_cluster/health" 2>/dev/null | grep -q '"status"'; do sleep 3; done
echo "OpenSearch hazir."

# Enable Elasticsearch compatibility for go-elasticsearch v8 client
os_api -X PUT "$OS_URL/_cluster/settings" \
  -H "Content-Type: application/json" -d '{
  "persistent": { "compatibility.override_main_response_version": true }
}' > /dev/null 2>&1

# Disable multi-tenancy at the security plugin level (runtime config patch).
# Replaces the need to mount a custom opensearch_dashboards.yml — Dashboards picks
# up the new value on subsequent requests; saved objects land in the global tenant
# index, so users see them without a tenant switcher.
echo "Multi-tenancy disable ediliyor..."
os_api -X PATCH "$OS_URL/_plugins/_security/api/securityconfig" \
  -H "Content-Type: application/json" -d '[{"op":"replace","path":"/config/dynamic/kibana/multitenancy_enabled","value":false}]' > /dev/null 2>&1
echo "Multi-tenancy disable edildi."

echo "OpenSearch Dashboards bekleniyor..."
until dash_api "$DASH_URL/api/status" 2>/dev/null | grep -q '"state"'; do sleep 5; done
echo "Dashboards hazir."

# ─────────────────── 2a. Kibana index mapping fix ────────────────────────────
# OpenSearch Dashboards tenant index may auto-create with "type" as text.
# "index-pattern" gets tokenized (hyphen) so _find API can't match it.
# Fix: create an index template that forces "type" to keyword, then recreate.
echo "Kibana index mapping duzeltiliyor..."
os_api -X PUT "$OS_URL/_index_template/kibana-type-keyword" \
  -H "Content-Type: application/json" -d '{
  "index_patterns": [".kibana*"],
  "priority": 0,
  "template": {
    "mappings": {
      "properties": {
        "type": { "type": "keyword" }
      }
    }
  }
}' > /dev/null 2>&1

echo "Kibana index mapping template olusturuldu."

# ─────────────────────────── 2. Idempotency Kontrolü ─────────────────────────
# Templates, roles and ISM policies are PUT-based, dashboards use overwrite=true,
# so the whole script is idempotent and re-runs safely on every container start.
echo "Setup baslatiliyor (idempotent re-run)..."

# Template above ensures correct mapping on new indices

# ─────────────────────────── 3. ISM Politikası ───────────────────────────────
echo "ISM politikasi olusturuluyor..."
os_api -X PUT "$OS_URL/_plugins/_ism/policies/finance-lifecycle" \
  -H "Content-Type: application/json" -d '{
  "policy": {
    "description": "Finance Portal telemetry lifecycle — hot 7d, delete 30d",
    "default_state": "hot",
    "ism_template": [
      { "index_patterns": ["finance-traces*", "finance-logs*", "finance-metrics*", "notification-traces*", "notification-logs*", "notification-metrics*"], "priority": 100 }
    ],
    "states": [
      {
        "name": "hot",
        "actions": [],
        "transitions": [{ "state_name": "delete", "conditions": { "min_index_age": "30d" } }]
      },
      {
        "name": "delete",
        "actions": [{ "delete": {} }],
        "transitions": []
      }
    ]
  }
}' > /dev/null 2>&1
echo "ISM politikasi olusturuldu."

# ─────────────────────────── 4. İndeks Şablonları ────────────────────────────
echo "Indeks sablonlari olusturuluyor..."

os_api -X PUT "$OS_URL/_index_template/finance-traces-template" \
  -H "Content-Type: application/json" -d '{
  "index_patterns": ["finance-traces*"],
  "template": {
    "settings": { "number_of_shards": 1, "number_of_replicas": 0 },
    "mappings": {
      "properties": {
        "traceId":        { "type": "keyword" },
        "spanId":         { "type": "keyword" },
        "parentSpanId":   { "type": "keyword" },
        "name":           { "type": "text", "fields": { "keyword": { "type": "keyword" } } },
        "kind":           { "type": "text", "fields": { "keyword": { "type": "keyword" } } },
        "startTime":      { "type": "date" },
        "endTime":        { "type": "date" },
        "status": {
          "properties": {
            "code":       { "type": "text", "fields": { "keyword": { "type": "keyword" } } },
            "message":    { "type": "text" }
          }
        },
        "resource": {
          "properties": {
            "service.name": { "type": "text", "fields": { "keyword": { "type": "keyword", "ignore_above": 256 } } }
          }
        },
        "attributes": {
          "properties": {
            "http.route":                   { "type": "text", "fields": { "keyword": { "type": "keyword" } } },
            "http.request.method":          { "type": "keyword" },
            "http.response.status_code":    { "type": "integer" },
            "url.path":                     { "type": "text", "fields": { "keyword": { "type": "keyword" } } }
          }
        }
      }
    }
  }
}' > /dev/null 2>&1

os_api -X PUT "$OS_URL/_index_template/finance-logs-template" \
  -H "Content-Type: application/json" -d '{
  "index_patterns": ["finance-logs*"],
  "template": {
    "settings": { "number_of_shards": 1, "number_of_replicas": 0 },
    "mappings": {
      "properties": {
        "@timestamp":      { "type": "date" },
        "severity":        { "type": "text", "fields": { "keyword": { "type": "keyword" } } },
        "body":            { "type": "text", "fields": { "keyword": { "type": "keyword", "ignore_above": 256 } } },
        "serviceName":     { "type": "keyword" },
        "logger":          { "type": "text", "fields": { "keyword": { "type": "keyword" } } },
        "thread":          { "type": "keyword" },
        "exception":       { "type": "text" }
      }
    }
  }
}' > /dev/null 2>&1

os_api -X PUT "$OS_URL/_index_template/finance-metrics-template" \
  -H "Content-Type: application/json" -d '{
  "index_patterns": ["finance-metrics*"],
  "template": {
    "settings": { "number_of_shards": 1, "number_of_replicas": 0 },
    "mappings": {
      "properties": {
        "@timestamp":                         { "type": "date" },
        "dropped":                            { "type": "boolean" },
        "service": {
          "properties": {
            "name":                           { "type": "keyword" },
            "namespace":                      { "type": "keyword" },
            "environment":                    { "type": "keyword" },
            "version":                        { "type": "keyword" }
          }
        },
        "host": {
          "properties": {
            "name":                           { "type": "keyword" },
            "hostname":                       { "type": "keyword" },
            "architecture":                   { "type": "keyword" }
          }
        },
        "container": {
          "properties": {
            "id":                             { "type": "keyword" }
          }
        },
        "jvm": {
          "properties": {
            "cpu": {
              "properties": {
                "count":                      { "type": "integer" },
                "recent_utilization":         { "type": "float" },
                "time":                       { "type": "float" }
              }
            },
            "memory": {
              "properties": {
                "committed":                  { "type": "long" },
                "used":                       { "type": "long" },
                "limit":                      { "type": "long" },
                "used_after_last_gc":         { "type": "long" },
                "type":                       { "type": "keyword" },
                "pool": {
                  "properties": {
                    "name":                   { "type": "keyword" }
                  }
                }
              }
            },
            "gc": {
              "properties": {
                "action":                     { "type": "keyword" },
                "name":                       { "type": "keyword" }
              }
            },
            "thread": {
              "properties": {
                "count":                      { "type": "long" },
                "daemon":                     { "type": "boolean" },
                "state":                      { "type": "keyword" }
              }
            },
            "class": {
              "properties": {
                "count":                      { "type": "long" },
                "loaded":                     { "type": "long" },
                "unloaded":                   { "type": "long" }
              }
            }
          }
        },
        "http": {
          "properties": {
            "request": {
              "properties": {
                "method":                     { "type": "keyword" }
              }
            },
            "response": {
              "properties": {
                "status_code":                { "type": "integer" }
              }
            },
            "route":                          { "type": "keyword" },
            "server": {
              "properties": {
                "request": {
                  "properties": {
                    "duration": {
                      "properties": {
                        "counts":             { "type": "long" },
                        "values":             { "type": "float" }
                      }
                    }
                  }
                }
              }
            }
          }
        },
        "db": {
          "properties": {
            "client": {
              "properties": {
                "connections": {
                  "properties": {
                    "max":                    { "type": "long" },
                    "usage":                  { "type": "long" },
                    "pending_requests":       { "type": "long" },
                    "idle": {
                      "properties": {
                        "min":                { "type": "long" }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}' > /dev/null 2>&1

echo "Finance indeks sablonlari olusturuldu."

# ─────────────────── 4a. Notification index templates (mirror finance) ──────
echo "Notification indeks sablonlari olusturuluyor..."

os_api -X PUT "$OS_URL/_index_template/notification-traces-template" \
  -H "Content-Type: application/json" -d '{
  "index_patterns": ["notification-traces*"],
  "template": {
    "settings": { "number_of_shards": 1, "number_of_replicas": 0 },
    "mappings": {
      "properties": {
        "traceId":        { "type": "keyword" },
        "spanId":         { "type": "keyword" },
        "parentSpanId":   { "type": "keyword" },
        "name":           { "type": "text", "fields": { "keyword": { "type": "keyword" } } },
        "kind":           { "type": "text", "fields": { "keyword": { "type": "keyword" } } },
        "startTime":      { "type": "date" },
        "endTime":        { "type": "date" },
        "status": {
          "properties": {
            "code":       { "type": "text", "fields": { "keyword": { "type": "keyword" } } },
            "message":    { "type": "text" }
          }
        },
        "resource": {
          "properties": {
            "service.name": { "type": "text", "fields": { "keyword": { "type": "keyword", "ignore_above": 256 } } }
          }
        },
        "attributes": {
          "properties": {
            "http.route":                   { "type": "text", "fields": { "keyword": { "type": "keyword" } } },
            "http.request.method":          { "type": "keyword" },
            "http.response.status_code":    { "type": "integer" },
            "url.path":                     { "type": "text", "fields": { "keyword": { "type": "keyword" } } }
          }
        }
      }
    }
  }
}' > /dev/null 2>&1

os_api -X PUT "$OS_URL/_index_template/notification-logs-template" \
  -H "Content-Type: application/json" -d '{
  "index_patterns": ["notification-logs*"],
  "template": {
    "settings": { "number_of_shards": 1, "number_of_replicas": 0 },
    "mappings": {
      "properties": {
        "@timestamp":      { "type": "date" },
        "severity":        { "type": "text", "fields": { "keyword": { "type": "keyword" } } },
        "body":            { "type": "text", "fields": { "keyword": { "type": "keyword", "ignore_above": 256 } } },
        "serviceName":     { "type": "keyword" },
        "logger":          { "type": "text", "fields": { "keyword": { "type": "keyword" } } },
        "thread":          { "type": "keyword" },
        "exception":       { "type": "text" }
      }
    }
  }
}' > /dev/null 2>&1

os_api -X PUT "$OS_URL/_index_template/notification-metrics-template" \
  -H "Content-Type: application/json" -d '{
  "index_patterns": ["notification-metrics*"],
  "template": {
    "settings": { "number_of_shards": 1, "number_of_replicas": 0 },
    "mappings": {
      "properties": {
        "@timestamp":                         { "type": "date" },
        "service": {
          "properties": {
            "name":                           { "type": "keyword" },
            "namespace":                      { "type": "keyword" },
            "environment":                    { "type": "keyword" }
          }
        },
        "jvm": {
          "properties": {
            "cpu": {
              "properties": {
                "count":                      { "type": "integer" },
                "recent_utilization":         { "type": "float" }
              }
            },
            "memory": {
              "properties": {
                "used":                       { "type": "long" },
                "type":                       { "type": "keyword" }
              }
            },
            "thread": {
              "properties": {
                "count":                      { "type": "long" },
                "state":                      { "type": "keyword" }
              }
            }
          }
        }
      }
    }
  }
}' > /dev/null 2>&1

echo "Notification indeks sablonlari olusturuldu."

# ─────────────────────────── 4b. Indeksler + Index Patterns ──────────────────
echo "Indeksler olusturuluyor..."

# Create empty indices (mappings applied from templates)
for idx in finance-traces finance-logs finance-metrics notification-traces notification-logs notification-metrics; do
  os_api -X PUT "$OS_URL/$idx" -H "Content-Type: application/json" -d '{}' > /dev/null 2>&1 || true
done

echo "Indeksler olusturuldu."

# ─────────────────────────── 5. Saved Objects NDJSON ─────────────────────────
echo "Index patternler, gorsellestirmeler ve dashboardlar hazirlaniyor..."

NDJSON="/tmp/otel_setup.ndjson"

# Dark-mode config fragment reused everywhere
DC=',"config":{"background":"transparent","view":{"stroke":"transparent"},"axis":{"domainColor":"#555","gridColor":"#333","tickColor":"#555","labelColor":"#ccc","titleColor":"#eee"},"legend":{"labelColor":"#ccc","titleColor":"#eee"},"title":{"color":"#eee","fontSize":14}}'

# ── Helper: write one index pattern line ──
ip_line() {
  _id="$1"; _title="$2"; _timefield="$3"
  jq -nc --arg id "$_id" --arg title "$_title" --arg tf "$_timefield" \
    '{"type":"index-pattern","id":$id,"attributes":{"title":$title,"timeFieldName":$tf}}'
}

# ── Helper: index pattern with duration_ms scripted field (trace patterns) ──
ip_line_traces() {
  _id="$1"; _title="$2"; _timefield="$3"
  _fields='[{"name":"duration_ms","type":"number","scripted":true,"script":"if(doc.containsKey('"'"'endTime'"'"')&&doc.containsKey('"'"'startTime'"'"')&&!doc['"'"'endTime'"'"'].empty&&!doc['"'"'startTime'"'"'].empty){return doc['"'"'endTime'"'"'].value.toInstant().toEpochMilli()-doc['"'"'startTime'"'"'].value.toInstant().toEpochMilli();}return 0;","lang":"painless","searchable":true,"aggregatable":true}]'
  jq -nc --arg id "$_id" --arg title "$_title" --arg tf "$_timefield" --arg fields "$_fields" \
    '{"type":"index-pattern","id":$id,"attributes":{"title":$title,"timeFieldName":$tf,"fields":$fields}}'
}

# ── Helper: write one visualization line ──
viz_line() {
  _id="$1"; _title="$2"; _spec="$3"
  jq -nc --arg id "$_id" --arg title "$_title" --arg spec "$_spec" \
    '{"type":"visualization","id":$id,"attributes":{"title":$title,"visState":( {"title":$title,"type":"vega","params":{"spec":$spec}} | tostring ),"uiStateJSON":"{}","description":"","kibanaSavedObjectMeta":{"searchSourceJSON":"{}"}}}'
}

# ── Helper: Visualize legacy donut pie with filter segments (uses Elastic Charts) ──
viz_legacy_pie() {
  _id="$1"; _title="$2"; _index="$3"; _filters_json="$4"
  jq -nc --arg id "$_id" --arg title "$_title" --arg idx "$_index" --argjson filters "$_filters_json" \
    '{
      "type":"visualization","id":$id,
      "attributes":{
        "title":$title,
        "visState":({"title":$title,"type":"pie","params":{"type":"pie","addTooltip":true,"addLegend":true,"legendPosition":"right","isDonut":true,"labels":{"show":true,"values":true,"last_level":true,"truncate":100,"valuesFormat":"percent","percentDecimals":1}},"aggs":[{"id":"1","enabled":true,"type":"count","schema":"metric","params":{}},{"id":"2","enabled":true,"type":"filters","schema":"segment","params":{"filters":$filters}}]} | tostring),
        "uiStateJSON":"{\"vis\":{\"colors\":{\"Success\":\"#54B399\",\"Error\":\"#E7664C\"}}}",
        "description":"",
        "kibanaSavedObjectMeta":{"searchSourceJSON":({"index":$idx,"query":{"query":"","language":"kuery"},"filter":[]} | tostring)}
      },
      "references":[{"name":"kibanaSavedObjectMeta.searchSourceJSON.index","type":"index-pattern","id":$idx}]
    }'
}

# ── Helper: Visualize legacy donut by terms aggregation ──
viz_legacy_pie_terms() {
  _id="$1"; _title="$2"; _index="$3"; _field="$4"; _size="${5:-10}"
  jq -nc --arg id "$_id" --arg title "$_title" --arg idx "$_index" --arg field "$_field" --argjson size "$_size" \
    '{"type":"visualization","id":$id,"attributes":{"title":$title,"visState":({"title":$title,"type":"pie","params":{"type":"pie","addTooltip":true,"addLegend":true,"legendPosition":"right","isDonut":true,"labels":{"show":true,"values":true,"last_level":true,"truncate":100,"valuesFormat":"percent","percentDecimals":1}},"aggs":[{"id":"1","enabled":true,"type":"count","schema":"metric","params":{}},{"id":"2","enabled":true,"type":"terms","schema":"segment","params":{"field":$field,"orderBy":"1","order":"desc","size":$size,"otherBucket":false,"missingBucket":false}}]}|tostring),"uiStateJSON":"{}","description":"","kibanaSavedObjectMeta":{"searchSourceJSON":({"index":$idx,"query":{"query":"","language":"kuery"},"filter":[]}|tostring)}},"references":[{"name":"kibanaSavedObjectMeta.searchSourceJSON.index","type":"index-pattern","id":$idx}]}'
}

# ── Helper: Visualize legacy table by terms with count metric ──
viz_legacy_table() {
  _id="$1"; _title="$2"; _index="$3"; _field="$4"; _size="${5:-10}"
  jq -nc --arg id "$_id" --arg title "$_title" --arg idx "$_index" --arg field "$_field" --argjson size "$_size" \
    '{"type":"visualization","id":$id,"attributes":{"title":$title,"visState":({"title":$title,"type":"table","params":{"perPage":($size|if .>20 then 20 else . end),"showPartialRows":false,"showMetricsAtAllLevels":false,"showTotal":true,"totalFunc":"sum","percentageCol":""},"aggs":[{"id":"1","enabled":true,"type":"count","schema":"metric","params":{}},{"id":"2","enabled":true,"type":"terms","schema":"bucket","params":{"field":$field,"orderBy":"1","order":"desc","size":$size,"otherBucket":false,"missingBucket":false}}]}|tostring),"uiStateJSON":"{}","description":"","kibanaSavedObjectMeta":{"searchSourceJSON":({"index":$idx,"query":{"query":"","language":"kuery"},"filter":[]}|tostring)}},"references":[{"name":"kibanaSavedObjectMeta.searchSourceJSON.index","type":"index-pattern","id":$idx}]}'
}

# ── Helper: Visualize legacy line — count metric over time ──
viz_legacy_line_count_time() {
  _id="$1"; _title="$2"; _index="$3"; _time_field="$4"; _label="${5:-Count}"; _query="${6:-}"
  jq -nc --arg id "$_id" --arg title "$_title" --arg idx "$_index" --arg tf "$_time_field" --arg label "$_label" --arg q "$_query" \
    '{"type":"visualization","id":$id,"attributes":{"title":$title,"visState":({"title":$title,"type":"line","params":{"type":"line","grid":{"categoryLines":false},"categoryAxes":[{"id":"CategoryAxis-1","type":"category","position":"bottom","show":true,"scale":{"type":"linear"},"labels":{"show":true,"filter":true,"truncate":100},"title":{}}],"valueAxes":[{"id":"ValueAxis-1","name":"LeftAxis-1","type":"value","position":"left","show":true,"scale":{"type":"linear","mode":"normal"},"labels":{"show":true,"rotate":0,"filter":false,"truncate":100},"title":{"text":$label}}],"seriesParams":[{"show":true,"type":"line","mode":"normal","data":{"label":$label,"id":"1"},"valueAxis":"ValueAxis-1","drawLinesBetweenPoints":true,"lineWidth":2,"interpolate":"linear","showCircles":true}],"addTooltip":true,"addLegend":true,"legendPosition":"right","times":[],"addTimeMarker":false,"thresholdLine":{"show":false}},"aggs":[{"id":"1","enabled":true,"type":"count","schema":"metric","params":{"customLabel":$label}},{"id":"2","enabled":true,"type":"date_histogram","schema":"segment","params":{"field":$tf,"useNormalizedOpenSearchInterval":true,"interval":"auto","drop_partials":false,"min_doc_count":1}}]}|tostring),"uiStateJSON":"{}","description":"","kibanaSavedObjectMeta":{"searchSourceJSON":({"index":$idx,"query":{"query":$q,"language":"kuery"},"filter":[]}|tostring)}},"references":[{"name":"kibanaSavedObjectMeta.searchSourceJSON.index","type":"index-pattern","id":$idx}]}'
}

# ── Helper: Visualize legacy line — avg(field) over time, optional terms split ──
viz_legacy_line_avg_time() {
  _id="$1"; _title="$2"; _index="$3"; _time_field="$4"; _metric_field="$5"; _label="${6:-Avg}"; _split_field="${7:-}"; _split_size="${8:-5}"; _query="${9:-}"
  jq -nc --arg id "$_id" --arg title "$_title" --arg idx "$_index" --arg tf "$_time_field" --arg mf "$_metric_field" --arg label "$_label" --arg sf "$_split_field" --argjson ss "$_split_size" --arg q "$_query" \
    '(if $sf=="" then [] else [{"id":"3","enabled":true,"type":"terms","schema":"group","params":{"field":$sf,"orderBy":"1","order":"desc","size":$ss,"otherBucket":false,"missingBucket":false}}] end) as $extra | {"type":"visualization","id":$id,"attributes":{"title":$title,"visState":({"title":$title,"type":"line","params":{"type":"line","grid":{"categoryLines":false},"categoryAxes":[{"id":"CategoryAxis-1","type":"category","position":"bottom","show":true,"scale":{"type":"linear"},"labels":{"show":true,"filter":true,"truncate":100},"title":{}}],"valueAxes":[{"id":"ValueAxis-1","name":"LeftAxis-1","type":"value","position":"left","show":true,"scale":{"type":"linear","mode":"normal"},"labels":{"show":true,"rotate":0,"filter":false,"truncate":100},"title":{"text":$label}}],"seriesParams":[{"show":true,"type":"line","mode":"normal","data":{"label":$label,"id":"1"},"valueAxis":"ValueAxis-1","drawLinesBetweenPoints":true,"lineWidth":2,"interpolate":"linear","showCircles":true}],"addTooltip":true,"addLegend":true,"legendPosition":"right","times":[],"addTimeMarker":false,"thresholdLine":{"show":false}},"aggs":([{"id":"1","enabled":true,"type":"avg","schema":"metric","params":{"field":$mf,"customLabel":$label}},{"id":"2","enabled":true,"type":"date_histogram","schema":"segment","params":{"field":$tf,"useNormalizedOpenSearchInterval":true,"interval":"auto","drop_partials":false,"min_doc_count":1}}]+$extra)}|tostring),"uiStateJSON":"{}","description":"","kibanaSavedObjectMeta":{"searchSourceJSON":({"index":$idx,"query":{"query":$q,"language":"kuery"},"filter":[]}|tostring)}},"references":[{"name":"kibanaSavedObjectMeta.searchSourceJSON.index","type":"index-pattern","id":$idx}]}'
}

# ── Helper: Visualize legacy line — percentiles over time ──
viz_legacy_line_percentiles_time() {
  _id="$1"; _title="$2"; _index="$3"; _time_field="$4"; _metric_field="$5"
  jq -nc --arg id "$_id" --arg title "$_title" --arg idx "$_index" --arg tf "$_time_field" --arg mf "$_metric_field" \
    '{"type":"visualization","id":$id,"attributes":{"title":$title,"visState":({"title":$title,"type":"line","params":{"type":"line","grid":{"categoryLines":false},"categoryAxes":[{"id":"CategoryAxis-1","type":"category","position":"bottom","show":true,"scale":{"type":"linear"},"labels":{"show":true,"filter":true,"truncate":100},"title":{}}],"valueAxes":[{"id":"ValueAxis-1","name":"LeftAxis-1","type":"value","position":"left","show":true,"scale":{"type":"linear","mode":"normal"},"labels":{"show":true,"rotate":0,"filter":false,"truncate":100},"title":{"text":"Latency (ms)"}}],"seriesParams":[{"show":true,"type":"line","mode":"normal","data":{"label":"P50","id":"1.50"},"valueAxis":"ValueAxis-1","drawLinesBetweenPoints":true,"lineWidth":2,"interpolate":"linear","showCircles":true},{"show":true,"type":"line","mode":"normal","data":{"label":"P95","id":"1.95"},"valueAxis":"ValueAxis-1","drawLinesBetweenPoints":true,"lineWidth":2,"interpolate":"linear","showCircles":true},{"show":true,"type":"line","mode":"normal","data":{"label":"P99","id":"1.99"},"valueAxis":"ValueAxis-1","drawLinesBetweenPoints":true,"lineWidth":2,"interpolate":"linear","showCircles":true}],"addTooltip":true,"addLegend":true,"legendPosition":"right","times":[],"addTimeMarker":false,"thresholdLine":{"show":false}},"aggs":[{"id":"1","enabled":true,"type":"percentiles","schema":"metric","params":{"field":$mf,"percents":[50,95,99]}},{"id":"2","enabled":true,"type":"date_histogram","schema":"segment","params":{"field":$tf,"useNormalizedOpenSearchInterval":true,"interval":"auto","drop_partials":false,"min_doc_count":1}}]}|tostring),"uiStateJSON":"{}","description":"","kibanaSavedObjectMeta":{"searchSourceJSON":({"index":$idx,"query":{"query":"","language":"kuery"},"filter":[]}|tostring)}},"references":[{"name":"kibanaSavedObjectMeta.searchSourceJSON.index","type":"index-pattern","id":$idx}]}'
}

# ── Helper: Visualize legacy area — count or avg metric over time, optional terms split ──
viz_legacy_area_time() {
  _id="$1"; _title="$2"; _index="$3"; _time_field="$4"; _metric_type="$5"; _metric_field="$6"; _label="${7:-Value}"; _split_field="${8:-}"; _split_size="${9:-5}"; _query="${10:-}"
  jq -nc --arg id "$_id" --arg title "$_title" --arg idx "$_index" --arg tf "$_time_field" --arg mt "$_metric_type" --arg mf "$_metric_field" --arg label "$_label" --arg sf "$_split_field" --argjson ss "$_split_size" --arg q "$_query" \
    '(if $mt=="count" then {"id":"1","enabled":true,"type":"count","schema":"metric","params":{"customLabel":$label}} else {"id":"1","enabled":true,"type":$mt,"schema":"metric","params":{"field":$mf,"customLabel":$label}} end) as $metric | (if $sf=="" then [] else [{"id":"3","enabled":true,"type":"terms","schema":"group","params":{"field":$sf,"orderBy":"1","order":"desc","size":$ss,"otherBucket":false,"missingBucket":false}}] end) as $extra | {"type":"visualization","id":$id,"attributes":{"title":$title,"visState":({"title":$title,"type":"area","params":{"type":"area","grid":{"categoryLines":false},"categoryAxes":[{"id":"CategoryAxis-1","type":"category","position":"bottom","show":true,"scale":{"type":"linear"},"labels":{"show":true,"filter":true,"truncate":100},"title":{}}],"valueAxes":[{"id":"ValueAxis-1","name":"LeftAxis-1","type":"value","position":"left","show":true,"scale":{"type":"linear","mode":"normal"},"labels":{"show":true,"rotate":0,"filter":false,"truncate":100},"title":{"text":$label}}],"seriesParams":[{"show":true,"type":"area","mode":"stacked","data":{"label":$label,"id":"1"},"valueAxis":"ValueAxis-1","drawLinesBetweenPoints":true,"lineWidth":2,"interpolate":"linear","showCircles":true}],"addTooltip":true,"addLegend":true,"legendPosition":"right","times":[],"addTimeMarker":false,"thresholdLine":{"show":false}},"aggs":([$metric,{"id":"2","enabled":true,"type":"date_histogram","schema":"segment","params":{"field":$tf,"useNormalizedOpenSearchInterval":true,"interval":"auto","drop_partials":false,"min_doc_count":1}}]+$extra)}|tostring),"uiStateJSON":"{}","description":"","kibanaSavedObjectMeta":{"searchSourceJSON":({"index":$idx,"query":{"query":$q,"language":"kuery"},"filter":[]}|tostring)}},"references":[{"name":"kibanaSavedObjectMeta.searchSourceJSON.index","type":"index-pattern","id":$idx}]}'
}

# ── Helper: Visualize legacy horizontal bar — terms bucket on Y, metric on X ──
viz_legacy_horizontal_bar() {
  _id="$1"; _title="$2"; _index="$3"; _bucket_field="$4"; _metric_type="${5:-count}"; _metric_field="${6:-}"; _size="${7:-10}"; _label="${8:-Count}"
  jq -nc --arg id "$_id" --arg title "$_title" --arg idx "$_index" --arg bf "$_bucket_field" --arg mt "$_metric_type" --arg mf "$_metric_field" --argjson size "$_size" --arg label "$_label" \
    '(if $mt=="count" then {"id":"1","enabled":true,"type":"count","schema":"metric","params":{"customLabel":$label}} else {"id":"1","enabled":true,"type":$mt,"schema":"metric","params":{"field":$mf,"customLabel":$label}} end) as $metric | {"type":"visualization","id":$id,"attributes":{"title":$title,"visState":({"title":$title,"type":"horizontal_bar","params":{"type":"histogram","grid":{"categoryLines":false},"categoryAxes":[{"id":"CategoryAxis-1","type":"category","position":"left","show":true,"scale":{"type":"linear"},"labels":{"show":true,"filter":true,"truncate":200,"rotate":0},"title":{}}],"valueAxes":[{"id":"ValueAxis-1","name":"BottomAxis-1","type":"value","position":"bottom","show":true,"scale":{"type":"linear","mode":"normal"},"labels":{"show":true,"rotate":0,"filter":false,"truncate":100},"title":{"text":$label}}],"seriesParams":[{"show":true,"type":"histogram","mode":"normal","data":{"label":$label,"id":"1"},"valueAxis":"ValueAxis-1","drawLinesBetweenPoints":true,"lineWidth":2,"showCircles":true}],"addTooltip":true,"addLegend":true,"legendPosition":"right","times":[],"addTimeMarker":false,"thresholdLine":{"show":false}},"aggs":[$metric,{"id":"2","enabled":true,"type":"terms","schema":"segment","params":{"field":$bf,"orderBy":"1","order":"desc","size":$size,"otherBucket":false,"missingBucket":false}}]}|tostring),"uiStateJSON":"{}","description":"","kibanaSavedObjectMeta":{"searchSourceJSON":({"index":$idx,"query":{"query":"","language":"kuery"},"filter":[]}|tostring)}},"references":[{"name":"kibanaSavedObjectMeta.searchSourceJSON.index","type":"index-pattern","id":$idx}]}'
}

# ── Helper: Visualize legacy horizontal bar from range agg (latency bands) ──
viz_legacy_horizontal_bar_range() {
  _id="$1"; _title="$2"; _index="$3"; _field="$4"; _ranges_json="$5"; _label="${6:-Count}"
  jq -nc --arg id "$_id" --arg title "$_title" --arg idx "$_index" --arg field "$_field" --argjson ranges "$_ranges_json" --arg label "$_label" \
    '{"type":"visualization","id":$id,"attributes":{"title":$title,"visState":({"title":$title,"type":"horizontal_bar","params":{"type":"histogram","grid":{"categoryLines":false},"categoryAxes":[{"id":"CategoryAxis-1","type":"category","position":"left","show":true,"scale":{"type":"linear"},"labels":{"show":true,"filter":true,"truncate":200,"rotate":0},"title":{}}],"valueAxes":[{"id":"ValueAxis-1","name":"BottomAxis-1","type":"value","position":"bottom","show":true,"scale":{"type":"linear","mode":"normal"},"labels":{"show":true,"rotate":0,"filter":false,"truncate":100},"title":{"text":$label}}],"seriesParams":[{"show":true,"type":"histogram","mode":"normal","data":{"label":$label,"id":"1"},"valueAxis":"ValueAxis-1","drawLinesBetweenPoints":true,"lineWidth":2,"showCircles":true}],"addTooltip":true,"addLegend":true,"legendPosition":"right","times":[],"addTimeMarker":false,"thresholdLine":{"show":false}},"aggs":[{"id":"1","enabled":true,"type":"count","schema":"metric","params":{"customLabel":$label}},{"id":"2","enabled":true,"type":"range","schema":"segment","params":{"field":$field,"ranges":$ranges}}]}|tostring),"uiStateJSON":"{}","description":"","kibanaSavedObjectMeta":{"searchSourceJSON":({"index":$idx,"query":{"query":"","language":"kuery"},"filter":[]}|tostring)}},"references":[{"name":"kibanaSavedObjectMeta.searchSourceJSON.index","type":"index-pattern","id":$idx}]}'
}

# ── Helper: write one dashboard line ──
dash_line() {
  _id="$1"; _title="$2"; shift 2
  _v1="$1"; _v2="$2"; _v3="$3"
  _panels='[{"gridData":{"x":0,"y":0,"w":24,"h":15,"i":"1"},"panelIndex":"1","embeddableConfig":{},"version":"2.19.1","panelRefName":"panel_0"},{"gridData":{"x":24,"y":0,"w":24,"h":15,"i":"2"},"panelIndex":"2","embeddableConfig":{},"version":"2.19.1","panelRefName":"panel_1"},{"gridData":{"x":0,"y":15,"w":48,"h":15,"i":"3"},"panelIndex":"3","embeddableConfig":{},"version":"2.19.1","panelRefName":"panel_2"}]'
  jq -nc --arg id "$_id" --arg title "$_title" --arg panels "$_panels" \
    --arg v1 "$_v1" --arg v2 "$_v2" --arg v3 "$_v3" \
    '{"type":"dashboard","id":$id,"attributes":{"title":$title,"hits":0,"description":"","panelsJSON":$panels,"optionsJSON":"{\"useMargins\":true,\"hidePanelTitles\":false}","timeRestore":true,"timeFrom":"now-24h","timeTo":"now","kibanaSavedObjectMeta":{"searchSourceJSON":"{}"}},"references":[{"name":"panel_0","type":"visualization","id":$v1},{"name":"panel_1","type":"visualization","id":$v2},{"name":"panel_2","type":"visualization","id":$v3}]}'
}

# ═══════════════ Build the NDJSON file ═══════════════

{
  # ─── INDEX PATTERNS ───
  ip_line_traces "finance-traces"      "finance-traces*"       "endTime"
  ip_line        "finance-logs"        "finance-logs*"         "@timestamp"
  ip_line        "finance-metrics"     "finance-metrics*"      "@timestamp"
  ip_line_traces "notification-traces" "notification-traces*"  "endTime"
  ip_line        "notification-logs"   "notification-logs*"    "@timestamp"
  ip_line        "notification-metrics" "notification-metrics*" "@timestamp"

  # ─── EXECUTIVE DASHBOARD CHARTS ───

  # 1.1 Success Rate Donut (Visualize legacy / Elastic Charts — sample for quality test)
  viz_legacy_pie "viz-exec-sr" "Executive - Success Rate" "finance-traces" \
    '[{"input":{"query":"NOT status.code.keyword:Error","language":"kuery"},"label":"Success"},{"input":{"query":"status.code.keyword:Error","language":"kuery"},"label":"Error"}]'

  # 1.2 Throughput RPM
  viz_legacy_line_count_time "viz-exec-rpm" "Executive - Throughput (RPM)" \
    "finance-traces" "endTime" "RPM"

  # 1.3 Service Uptime Timeline (span volume split by service name)
  viz_legacy_area_time "viz-exec-uptime" "Finance Backend - Service Uptime" \
    "finance-traces" "endTime" "count" "" "Spans" "resource.service.name.keyword" 5 ""

  # ─── APM DASHBOARD CHARTS ───

  # 2.1 Latency Percentiles (uses duration_ms scripted field)
  viz_legacy_line_percentiles_time "viz-apm-lat" "APM - Latency Percentiles" \
    "finance-traces" "endTime" "duration_ms"

  # 2.2 Latency Heatmap — horizontal bar over latency range buckets
  viz_legacy_horizontal_bar_range "viz-apm-hm" "APM - Latency Heatmap" \
    "finance-traces" "duration_ms" \
    '[{"from":0,"to":10},{"from":10,"to":50},{"from":50,"to":100},{"from":100,"to":300},{"from":300,"to":1000},{"from":1000}]' \
    "Count"

  # 2.3 Dependency Analysis — top span operations by avg latency
  # name.keyword is populated on every span (HTTP, Kafka, scheduler, JPA);
  # attributes.http.route.keyword exists on only ~0.01% of docs so it produced an empty chart.
  viz_legacy_horizontal_bar "viz-apm-dep" "APM - Dependency Analysis" \
    "finance-traces" "name.keyword" "avg" "duration_ms" 20 "Avg ms"

  # ─── RELIABILITY DASHBOARD CHARTS ───

  # 3.1 Log Severity Distribution — donut by severity terms
  viz_legacy_pie_terms "viz-rel-logdist" "Reliability - Log Distribution" \
    "finance-logs" "severity.keyword" 10

  # 3.2 Log Volume Over Time — stacked area split by severity
  viz_legacy_area_time "viz-rel-errhm" "Reliability - Error Heatmap" \
    "finance-logs" "@timestamp" "count" "" "Log Count" "severity.keyword" 5 ""

  # 3.3 Top Log Messages — horizontal bar by body
  viz_legacy_horizontal_bar "viz-rel-toperr" "Reliability - Top Log Messages" \
    "finance-logs" "body.keyword" "count" "" 10 "Occurrences"
  # ─── METRICS DASHBOARD CHARTS ───

  # 4.1 CPU Usage Trend — area, avg(jvm.cpu.recent_utilization). Value is 0-1 ratio.
  viz_legacy_area_time "viz-met-cpu" "Metrics - CPU Usage" \
    "finance-metrics" "@timestamp" "avg" "jvm.cpu.recent_utilization" "CPU (0-1)" "" 5 ""

  # 4.2 Memory Usage — area, sum(jvm.memory.used) for heap (bytes)
  viz_legacy_area_time "viz-met-mem" "Metrics - Memory Usage" \
    "finance-metrics" "@timestamp" "sum" "jvm.memory.used" "Heap Bytes" "" 5 "jvm.memory.type:heap"

  # 4.3 HTTP Requests — line, count over time, filtered for request counter docs
  viz_legacy_line_count_time "viz-met-reqcount" "Metrics - Request Count" \
    "finance-metrics" "@timestamp" "Requests" "http.server.request.duration.counts:*"

  # 4.4 Error Rate Trend — line of error count on traces (filter:Error)
  viz_legacy_line_count_time "viz-met-errrate" "Metrics - Error Rate Trend" \
    "finance-traces" "endTime" "Errors" "status.code.keyword:Error"

  # ─── DASHBOARDS ───
  dash_line "otel-exec-dashboard" "1. Finance Backend - Executive Health" "viz-exec-sr" "viz-exec-rpm" "viz-exec-uptime"
  dash_line "otel-apm-dashboard"  "2. Finance Backend - APM & Performance" "viz-apm-lat" "viz-apm-hm" "viz-apm-dep"
  dash_line "otel-rel-dashboard"  "3. Finance Backend - Reliability & Logs" "viz-rel-logdist" "viz-rel-errhm" "viz-rel-toperr"

  # 4th dashboard with 4 panels
  _panels4='[{"gridData":{"x":0,"y":0,"w":24,"h":15,"i":"1"},"panelIndex":"1","embeddableConfig":{},"version":"2.19.1","panelRefName":"panel_0"},{"gridData":{"x":24,"y":0,"w":24,"h":15,"i":"2"},"panelIndex":"2","embeddableConfig":{},"version":"2.19.1","panelRefName":"panel_1"},{"gridData":{"x":0,"y":15,"w":24,"h":15,"i":"3"},"panelIndex":"3","embeddableConfig":{},"version":"2.19.1","panelRefName":"panel_2"},{"gridData":{"x":24,"y":15,"w":24,"h":15,"i":"4"},"panelIndex":"4","embeddableConfig":{},"version":"2.19.1","panelRefName":"panel_3"}]'
  jq -nc --arg id "otel-met-dashboard" --arg title "4. Metrics & System Resources" --arg panels "$_panels4" \
    --arg v1 "viz-met-cpu" --arg v2 "viz-met-mem" --arg v3 "viz-met-reqcount" --arg v4 "viz-met-errrate" \
    '{"type":"dashboard","id":$id,"attributes":{"title":$title,"hits":0,"description":"","panelsJSON":$panels,"optionsJSON":"{\"useMargins\":true,\"hidePanelTitles\":false}","timeRestore":true,"timeFrom":"now-24h","timeTo":"now","kibanaSavedObjectMeta":{"searchSourceJSON":"{}"}},"references":[{"name":"panel_0","type":"visualization","id":$v1},{"name":"panel_1","type":"visualization","id":$v2},{"name":"panel_2","type":"visualization","id":$v3},{"name":"panel_3","type":"visualization","id":$v4}]}'

  # ─── INTEGRATION & DB DASHBOARD CHARTS ───

  # 5.1 DB Connection Pool Health — three avg metrics (active/pending/max) on shared time axis
  jq -nc --arg id "viz-integ-dbpool" --arg title "Integration - DB Connection Pool" --arg idx "finance-metrics" \
    '{"type":"visualization","id":$id,"attributes":{"title":$title,"visState":({"title":$title,"type":"line","params":{"type":"line","grid":{"categoryLines":false},"categoryAxes":[{"id":"CategoryAxis-1","type":"category","position":"bottom","show":true,"scale":{"type":"linear"},"labels":{"show":true,"filter":true,"truncate":100},"title":{}}],"valueAxes":[{"id":"ValueAxis-1","name":"LeftAxis-1","type":"value","position":"left","show":true,"scale":{"type":"linear","mode":"normal"},"labels":{"show":true,"rotate":0,"filter":false,"truncate":100},"title":{"text":"Connections"}}],"seriesParams":[{"show":true,"type":"line","mode":"normal","data":{"label":"Active","id":"1"},"valueAxis":"ValueAxis-1","drawLinesBetweenPoints":true,"lineWidth":2,"interpolate":"linear","showCircles":true},{"show":true,"type":"line","mode":"normal","data":{"label":"Pending","id":"3"},"valueAxis":"ValueAxis-1","drawLinesBetweenPoints":true,"lineWidth":2,"interpolate":"linear","showCircles":true},{"show":true,"type":"line","mode":"normal","data":{"label":"Max","id":"4"},"valueAxis":"ValueAxis-1","drawLinesBetweenPoints":true,"lineWidth":2,"interpolate":"linear","showCircles":true}],"addTooltip":true,"addLegend":true,"legendPosition":"right","times":[],"addTimeMarker":false,"thresholdLine":{"show":false}},"aggs":[{"id":"1","enabled":true,"type":"avg","schema":"metric","params":{"field":"db.client.connections.usage","customLabel":"Active"}},{"id":"3","enabled":true,"type":"avg","schema":"metric","params":{"field":"db.client.connections.pending_requests","customLabel":"Pending"}},{"id":"4","enabled":true,"type":"avg","schema":"metric","params":{"field":"db.client.connections.max","customLabel":"Max"}},{"id":"2","enabled":true,"type":"date_histogram","schema":"segment","params":{"field":"@timestamp","useNormalizedOpenSearchInterval":true,"interval":"auto","drop_partials":false,"min_doc_count":1}}]}|tostring),"uiStateJSON":"{}","description":"","kibanaSavedObjectMeta":{"searchSourceJSON":({"index":$idx,"query":{"query":"","language":"kuery"},"filter":[]}|tostring)}},"references":[{"name":"kibanaSavedObjectMeta.searchSourceJSON.index","type":"index-pattern","id":$idx}]}'

  # 5.2 JVM Thread State Distribution — donut by jvm.thread.state, sum(jvm.thread.count)
  jq -nc --arg id "viz-integ-threads" --arg title "Integration - Thread States" --arg idx "finance-metrics" \
    '{"type":"visualization","id":$id,"attributes":{"title":$title,"visState":({"title":$title,"type":"pie","params":{"type":"pie","addTooltip":true,"addLegend":true,"legendPosition":"right","isDonut":true,"labels":{"show":true,"values":true,"last_level":true,"truncate":100,"valuesFormat":"percent","percentDecimals":1}},"aggs":[{"id":"1","enabled":true,"type":"sum","schema":"metric","params":{"field":"jvm.thread.count","customLabel":"Threads"}},{"id":"2","enabled":true,"type":"terms","schema":"segment","params":{"field":"jvm.thread.state","orderBy":"1","order":"desc","size":10,"otherBucket":false,"missingBucket":false}}]}|tostring),"uiStateJSON":"{}","description":"","kibanaSavedObjectMeta":{"searchSourceJSON":({"index":$idx,"query":{"query":"","language":"kuery"},"filter":[]}|tostring)}},"references":[{"name":"kibanaSavedObjectMeta.searchSourceJSON.index","type":"index-pattern","id":$idx}]}'

  # 5.3 HTTP Server Request Duration Over Time — area avg(http.server.request.duration.values), label seconds
  viz_legacy_area_time "viz-integ-httplat" "Integration - HTTP Latency Trend" \
    "finance-metrics" "@timestamp" "avg" "http.server.request.duration.values" "Avg seconds" "" 5 ""

  # 5th dashboard with 3 panels
  dash_line "otel-integ-dashboard" "5. Integration & DB Health" "viz-integ-dbpool" "viz-integ-threads" "viz-integ-httplat"

  # ─── Notification Dashboards ─────────────────────────

  # N1.1 Notification Success Rate Donut (Visualize legacy / Elastic Charts — sample for quality test)
  viz_legacy_pie "viz-notif-exec-sr" "Notification Executive - Success Rate" "notification-traces" \
    '[{"input":{"query":"NOT status.code.keyword:Error","language":"kuery"},"label":"Success"},{"input":{"query":"status.code.keyword:Error","language":"kuery"},"label":"Error"}]'

  # N1.2 Notification Throughput RPM
  viz_legacy_line_count_time "viz-notif-exec-rpm" "Notification Executive - Throughput (RPM)" \
    "notification-traces" "endTime" "RPM"

  # N1.3 Notification Service Uptime Timeline (span volume split by service name)
  viz_legacy_area_time "viz-notif-exec-uptime" "Finance Notification - Service Uptime" \
    "notification-traces" "endTime" "count" "" "Spans" "resource.service.name.keyword" 5 ""

  # N2.1 Notification Latency Percentiles (uses duration_ms scripted field)
  viz_legacy_line_percentiles_time "viz-notif-apm-lat" "Notification APM - Latency Percentiles" \
    "notification-traces" "endTime" "duration_ms"

  # N2.2 Notification Latency Heatmap — horizontal bar over latency range buckets
  viz_legacy_horizontal_bar_range "viz-notif-apm-hm" "Notification APM - Latency Heatmap" \
    "notification-traces" "duration_ms" \
    '[{"from":0,"to":10},{"from":10,"to":50},{"from":50,"to":100},{"from":100,"to":300},{"from":300,"to":1000},{"from":1000}]' \
    "Count"

  # N2.3 Notification Dependency Analysis — top span operations by avg latency
  viz_legacy_horizontal_bar "viz-notif-apm-dep" "Notification APM - Dependency Analysis" \
    "notification-traces" "name.keyword" "avg" "duration_ms" 20 "Avg ms"

  # N3.1 Notification Log Severity Distribution — donut by severity terms
  viz_legacy_pie_terms "viz-notif-rel-logdist" "Notification Reliability - Log Distribution" \
    "notification-logs" "severity.keyword" 10

  # N3.2 Notification Log Volume Over Time — stacked area split by severity
  viz_legacy_area_time "viz-notif-rel-errhm" "Notification Reliability - Error Heatmap" \
    "notification-logs" "@timestamp" "count" "" "Log Count" "severity.keyword" 5 ""

  # N3.3 Notification Top Log Messages — horizontal bar by body
  viz_legacy_horizontal_bar "viz-notif-rel-toperr" "Notification Reliability - Top Log Messages" \
    "notification-logs" "body.keyword" "count" "" 10 "Occurrences"

  # N4.1 Notification CPU — area, avg(jvm.cpu.recent_utilization). Value is 0-1 ratio.
  viz_legacy_area_time "viz-notif-met-cpu" "Notification Metrics - CPU Usage" \
    "notification-metrics" "@timestamp" "avg" "jvm.cpu.recent_utilization" "CPU (0-1)" "" 5 ""

  # N4.2 Notification Memory — area, sum(jvm.memory.used) for heap (bytes)
  viz_legacy_area_time "viz-notif-met-mem" "Notification Metrics - Memory Usage" \
    "notification-metrics" "@timestamp" "sum" "jvm.memory.used" "Heap Bytes" "" 5 "jvm.memory.type:heap"

  # N4.3 Notification Request Count — line, count over time, filtered for request counter docs
  viz_legacy_line_count_time "viz-notif-met-reqcount" "Notification Metrics - Request Count" \
    "notification-metrics" "@timestamp" "Requests" "http.server.request.duration.counts:*"

  # N4.4 Notification Error Rate Trend — line of error count on traces (filter:Error)
  viz_legacy_line_count_time "viz-notif-met-errrate" "Notification Metrics - Error Rate Trend" \
    "notification-traces" "endTime" "Errors" "status.code.keyword:Error"

  # N5.1 Notification Domain - by Logger (PriceAlertEvaluator/WatchlistEvaluator/NotificationDispatcher)
  viz_legacy_horizontal_bar "viz-notif-domain-types" "Notification Domain - By Logger" \
    "notification-logs" "logger.keyword" "count" "" 15 "Events"

  # N5.2 Notification Domain - Top Events from Logs
  viz_legacy_horizontal_bar "viz-notif-domain-events" "Notification Domain - Top Events" \
    "notification-logs" "body.keyword" "count" "" 15 "Occurrences"

  # N5.3 Notification Domain - Severity Over Time — stacked area split by severity
  viz_legacy_area_time "viz-notif-domain-sev" "Notification Domain - Severity Over Time" \
    "notification-logs" "@timestamp" "count" "" "Log Count" "severity.keyword" 5 ""

  # ─── Notification Dashboards (mirror finance dashboards) ───
  dash_line "notif-exec-dashboard"   "1. Notification Executive Health"   "viz-notif-exec-sr"      "viz-notif-exec-rpm"   "viz-notif-exec-uptime"
  dash_line "notif-apm-dashboard"    "2. Notification APM & Performance"  "viz-notif-apm-lat"      "viz-notif-apm-hm"     "viz-notif-apm-dep"
  dash_line "notif-rel-dashboard"    "3. Notification Reliability & Logs" "viz-notif-rel-logdist"  "viz-notif-rel-errhm"  "viz-notif-rel-toperr"

  # Notification 4-panel metrics dashboard (mirrors otel-met-dashboard layout)
  jq -nc --arg id "notif-met-dashboard" --arg title "4. Notification Metrics & System" --arg panels "$_panels4" \
    --arg v1 "viz-notif-met-cpu" --arg v2 "viz-notif-met-mem" --arg v3 "viz-notif-met-reqcount" --arg v4 "viz-notif-met-errrate" \
    '{"type":"dashboard","id":$id,"attributes":{"title":$title,"hits":0,"description":"","panelsJSON":$panels,"optionsJSON":"{\"useMargins\":true,\"hidePanelTitles\":false}","timeRestore":true,"timeFrom":"now-24h","timeTo":"now","kibanaSavedObjectMeta":{"searchSourceJSON":"{}"}},"references":[{"name":"panel_0","type":"visualization","id":$v1},{"name":"panel_1","type":"visualization","id":$v2},{"name":"panel_2","type":"visualization","id":$v3},{"name":"panel_3","type":"visualization","id":$v4}]}'

  dash_line "notif-domain-dashboard" "5. Notification Domain Insights"    "viz-notif-domain-types" "viz-notif-domain-events" "viz-notif-domain-sev"

} > "$NDJSON"

# Validate every line is valid JSON
echo "NDJSON dogrulaniyor..."
LINE_NUM=0
while IFS= read -r line; do
  LINE_NUM=$((LINE_NUM + 1))
  if ! printf '%s' "$line" | jq empty 2>/dev/null; then
    echo "HATA: Satir $LINE_NUM gecersiz JSON!"
    printf '%s\n' "$line" | head -c 300
    exit 1
  fi
done < "$NDJSON"
echo "Tum $LINE_NUM satir gecerli JSON."

# ─────────────────────────── 6. Import Et ────────────────────────────────────
echo "Saved objects import ediliyor..."
IMPORT_RESULT=$(dash_api -X POST "$DASH_URL/api/saved_objects/_import?overwrite=true" \
  -F "file=@$NDJSON" 2>/dev/null)

SUCCESS=$(printf '%s' "$IMPORT_RESULT" | jq -r '.success // false' 2>/dev/null || echo "false")

if [ "$SUCCESS" = "true" ]; then
  # Refresh field lists — index templates give us the mapping even without docs
  echo "Index pattern field listleri guncelleniyor..."
  for pat_id in finance-traces finance-logs finance-metrics notification-traces notification-logs notification-metrics; do
    case "$pat_id" in
      finance-traces|notification-traces) _tf="endTime" ;;
      *)                                  _tf="@timestamp" ;;
    esac

    # Trace patterns retain a duration_ms scripted field so APM viz can
    # use it as a metric without re-declaring the painless script everywhere.
    case "$pat_id" in
      finance-traces|notification-traces)
        _scripted='[{"name":"duration_ms","type":"number","scripted":true,"script":"if(doc.containsKey('"'"'endTime'"'"')&&doc.containsKey('"'"'startTime'"'"')&&!doc['"'"'endTime'"'"'].empty&&!doc['"'"'startTime'"'"'].empty){return doc['"'"'endTime'"'"'].value.toInstant().toEpochMilli()-doc['"'"'startTime'"'"'].value.toInstant().toEpochMilli();}return 0;","lang":"painless","searchable":true,"aggregatable":true}]'
        ;;
      *) _scripted='[]' ;;
    esac

    # Build fields JSON from _field_caps and merge with scripted fields
    FIELDS_JSON=$(os_api -s "$OS_URL/${pat_id}*/_field_caps?fields=*" \
      -H 'Content-Type: application/json' 2>/dev/null \
    | jq -c --argjson scripted "$_scripted" '[
        .fields | to_entries[] |
        .key as $name | .value | to_entries[0] |
        .value as $info |
        {
          name: $name,
          type: (if $info.type == "keyword" or $info.type == "text" then "string"
                 elif $info.type == "integer" or $info.type == "long" or $info.type == "float" or $info.type == "double" then "number"
                 elif $info.type == "date" then "date"
                 elif $info.type == "boolean" then "boolean"
                 else $info.type end),
          count: 0,
          scripted: false,
          searchable: ($info.searchable // true),
          aggregatable: ($info.aggregatable // false),
          readFromDocValues: ($info.aggregatable // false)
        }
      ] + $scripted')

    # Use jq to build payload — handles JSON escaping correctly
    PAYLOAD=$(jq -nc \
      --arg fields "$FIELDS_JSON" \
      --arg title "${pat_id}*" \
      --arg tf "$_tf" \
      '{attributes: {title: $title, timeFieldName: $tf, fields: $fields}}')

    dash_api -X PUT "$DASH_URL/api/saved_objects/index-pattern/$pat_id" \
      -H "Content-Type: application/json" \
      -d "$PAYLOAD" > /dev/null 2>&1 && echo "  $pat_id: OK" || echo "  $pat_id: HATA"
  done
  echo "Kurulum tamamlandi! 10 Dashboard ve 32 Grafik basariyla olusturuldu."
  echo ""
  echo "   Finance Dashboards:"
  echo "   1. Executive Health Dashboard     - Genel saglik durumu"
  echo "   2. APM & Performance Dashboard    - Performans analizi"
  echo "   3. Reliability & Logs Dashboard   - Hata analizi"
  echo "   4. Metrics & System Resources     - Sistem kaynaklari"
  echo "   5. Integration & DB Health        - DB pool, thread, HTTP latency"
  echo ""
  echo "   Notification Dashboards:"
  echo "   1. Notification Executive Health  - Genel saglik durumu"
  echo "   2. Notification APM & Performance - Performans analizi"
  echo "   3. Notification Reliability & Logs- Hata analizi"
  echo "   4. Notification Metrics & System  - Sistem kaynaklari"
  echo "   5. Notification Domain Insights   - Logger, top events, severity"
  echo ""
  echo "   http://opensearch-dashboards:5601/app/dashboards"
else
  echo "UYARI: Import sirasinda sorun olustu:"
  printf '%s' "$IMPORT_RESULT" | jq '.' 2>/dev/null || printf '%s\n' "$IMPORT_RESULT"
  exit 1
fi