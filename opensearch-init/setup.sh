#!/bin/sh
set -e


# Finance Portal — OpenSearch Init Script
# Creates: ISM Policy, Index Templates, Index Patterns, 13 Visualizations, 4 Dashboards
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
echo "Mevcut dashboardlar kontrol ediliyor..."
DASH_CHECK=$(dash_api "$DASH_URL/api/saved_objects/dashboard/otel-exec-dashboard" 2>/dev/null | jq -r '.id // empty' 2>/dev/null || true)

if [ "$DASH_CHECK" = "otel-exec-dashboard" ]; then
  echo "Dashboardlar zaten yuklu. Kurulum atlaniyor."
  exit 0
fi

echo "Dashboardlar bulunamadi. Ilk kurulum baslatiliyor..."

# Template above ensures correct mapping on new indices

# ─────────────────────────── 3. ISM Politikası ───────────────────────────────
echo "ISM politikasi olusturuluyor..."
os_api -X PUT "$OS_URL/_plugins/_ism/policies/finance-lifecycle" \
  -H "Content-Type: application/json" -d '{
  "policy": {
    "description": "Finance Portal telemetry lifecycle — hot 7d, delete 30d",
    "default_state": "hot",
    "ism_template": [
      { "index_patterns": ["finance-traces*", "finance-logs*", "finance-metrics*"], "priority": 100 }
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
            "service.name": { "type": "keyword" }
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
        "logger":          { "type": "keyword" },
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

echo "Indeks sablonlari olusturuldu."

# ─────────────────────────── 4b. Indeksler + Index Patterns ──────────────────
echo "Indeksler olusturuluyor..."

# Create empty indices (mappings applied from templates)
for idx in finance-traces finance-logs finance-metrics; do
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

# ── Helper: write one visualization line ──
viz_line() {
  _id="$1"; _title="$2"; _spec="$3"
  jq -nc --arg id "$_id" --arg title "$_title" --arg spec "$_spec" \
    '{"type":"visualization","id":$id,"attributes":{"title":$title,"visState":( {"title":$title,"type":"vega","params":{"spec":$spec}} | tostring ),"uiStateJSON":"{}","description":"","kibanaSavedObjectMeta":{"searchSourceJSON":"{}"}}}'
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
  ip_line "finance-traces"  "finance-traces*"  "endTime"
  ip_line "finance-logs"    "finance-logs*"    "@timestamp"
  ip_line "finance-metrics" "finance-metrics*" "@timestamp"

  # ─── EXECUTIVE DASHBOARD CHARTS ───

  # 1.1 Success Rate Gauge
  viz_line "viz-exec-sr" "Executive - Success Rate" \
    '{"$schema":"https://vega.github.io/schema/vega-lite/v5.json"'"$DC"',"title":"Success Rate","data":{"url":{"%context%":true,"index":"finance-traces*","body":{"size":0,"aggs":{"total":{"value_count":{"field":"traceId"}},"errors":{"filter":{"term":{"status.code.keyword":"Error"}}}}}},"format":{"property":"aggregations"}},"transform":[{"calculate":"datum.total.value > 0 ? ((datum.total.value - datum.errors.doc_count) / datum.total.value) * 100 : 100","as":"success_pct"},{"calculate":"datum.total.value > 0 ? (datum.errors.doc_count / datum.total.value) * 100 : 0","as":"error_pct"},{"fold":["success_pct","error_pct"]}],"mark":{"type":"arc","innerRadius":60,"outerRadius":90,"cornerRadius":4},"encoding":{"theta":{"field":"value","type":"quantitative","stack":true},"color":{"field":"key","type":"nominal","scale":{"domain":["success_pct","error_pct"],"range":["#54B399","#E7664C"]},"legend":{"title":"Status"}}}}'

  # 1.2 Throughput RPM
  viz_line "viz-exec-rpm" "Executive - Throughput (RPM)" \
    '{"$schema":"https://vega.github.io/schema/vega-lite/v5.json"'"$DC"',"title":"Throughput (RPM)","data":{"url":{"%context%":true,"index":"finance-traces*","body":{"size":0,"aggs":{"rpm":{"date_histogram":{"field":"endTime","fixed_interval":"1m"}}}}},"format":{"property":"aggregations.rpm.buckets"}},"mark":{"type":"bar","cornerRadiusTopLeft":3,"cornerRadiusTopRight":3,"color":{"gradient":"linear","stops":[{"offset":0,"color":"#1B6B93"},{"offset":1,"color":"#54B399"}]}},"encoding":{"x":{"field":"key","type":"temporal","axis":{"title":"Time","format":"%H:%M"}},"y":{"field":"doc_count","type":"quantitative","axis":{"title":"Requests / Minute"}},"tooltip":[{"field":"key","type":"temporal","title":"Time"},{"field":"doc_count","type":"quantitative","title":"RPM"}]}}'

  # 1.3 Service Uptime Timeline
  viz_line "viz-exec-uptime" "Executive - Service Uptime" \
    '{"$schema":"https://vega.github.io/schema/vega-lite/v5.json"'"$DC"',"title":"Service Uptime Timeline","data":{"url":{"%context%":true,"index":"finance-traces*","body":{"size":0,"aggs":{"timeline":{"date_histogram":{"field":"endTime","fixed_interval":"5m"},"aggs":{"errors":{"filter":{"term":{"status.code.keyword":"Error"}}}}}}}},"format":{"property":"aggregations.timeline.buckets"}},"transform":[{"calculate":"datum.errors.doc_count > 0 ? '\''Degraded'\'' : '\''Healthy'\''","as":"status"}],"mark":{"type":"rect","height":30,"cornerRadius":3},"encoding":{"x":{"field":"key","type":"temporal","axis":{"title":"Time","format":"%H:%M"}},"color":{"field":"status","type":"nominal","scale":{"domain":["Healthy","Degraded"],"range":["#54B399","#E7664C"]},"legend":{"title":"Status"}},"tooltip":[{"field":"key","type":"temporal","title":"Time"},{"field":"status","type":"nominal","title":"Status"},{"field":"doc_count","type":"quantitative","title":"Requests"}]}}'

  # ─── APM DASHBOARD CHARTS ───

  # 2.1 Latency Percentiles (uses script to compute duration from startTime/endTime)
  viz_line "viz-apm-lat" "APM - Latency Percentiles" \
    '{"$schema":"https://vega.github.io/schema/vega-lite/v5.json"'"$DC"',"title":"Latency Percentiles (ms)","data":{"url":{"%context%":true,"index":"finance-traces*","body":{"size":0,"aggs":{"over_time":{"date_histogram":{"field":"endTime","fixed_interval":"1m","min_doc_count":1},"aggs":{"pcts":{"percentiles":{"script":{"source":"doc['\''endTime'\''].value.toInstant().toEpochMilli() - doc['\''startTime'\''].value.toInstant().toEpochMilli()","lang":"painless"},"percents":[50,95,99]}}}}}}},"format":{"property":"aggregations.over_time.buckets"}},"transform":[{"calculate":"datum.pcts.values['\''50.0'\''] != null && isFinite(datum.pcts.values['\''50.0'\'']) ? datum.pcts.values['\''50.0'\''] : 0","as":"P50"},{"calculate":"datum.pcts.values['\''95.0'\''] != null && isFinite(datum.pcts.values['\''95.0'\'']) ? datum.pcts.values['\''95.0'\''] : 0","as":"P95"},{"calculate":"datum.pcts.values['\''99.0'\''] != null && isFinite(datum.pcts.values['\''99.0'\'']) ? datum.pcts.values['\''99.0'\''] : 0","as":"P99"}],"layer":[{"mark":{"type":"line","strokeWidth":2,"point":{"size":30},"color":"#54B399"},"encoding":{"y":{"field":"P50","type":"quantitative"},"tooltip":[{"field":"key","type":"temporal","title":"Time"},{"field":"P50","type":"quantitative","title":"P50 ms","format":".1f"}]}},{"mark":{"type":"line","strokeWidth":2,"point":{"size":30},"color":"#D6BF57"},"encoding":{"y":{"field":"P95","type":"quantitative"},"tooltip":[{"field":"key","type":"temporal","title":"Time"},{"field":"P95","type":"quantitative","title":"P95 ms","format":".1f"}]}},{"mark":{"type":"line","strokeWidth":2,"point":{"size":30},"color":"#E7664C"},"encoding":{"y":{"field":"P99","type":"quantitative"},"tooltip":[{"field":"key","type":"temporal","title":"Time"},{"field":"P99","type":"quantitative","title":"P99 ms","format":".1f"}]}}],"encoding":{"x":{"field":"key","type":"temporal","axis":{"title":"Time","format":"%H:%M"}},"y":{"type":"quantitative","axis":{"title":"Latency (ms)"}}}}'

  # 2.2 Latency Heatmap (computes duration client-side from startTime/endTime)
  viz_line "viz-apm-hm" "APM - Latency Heatmap" \
    '{"$schema":"https://vega.github.io/schema/vega-lite/v5.json"'"$DC"',"title":"APM - Latency Heatmap","data":{"url":{"%context%":true,"index":"finance-traces*","body":{"size":0,"aggs":{"over_time":{"date_histogram":{"field":"endTime","calendar_interval":"1m","min_doc_count":1},"aggs":{"lat_ranges":{"range":{"script":{"source":"doc['\''endTime'\''].value.toInstant().toEpochMilli() - doc['\''startTime'\''].value.toInstant().toEpochMilli()","lang":"painless"},"ranges":[{"key":"0-10 ms","to":10},{"key":"10-50 ms","from":10,"to":50},{"key":"50-100 ms","from":50,"to":100},{"key":"100-300 ms","from":100,"to":300},{"key":"300-1000 ms","from":300,"to":1000},{"key":"1s+","from":1000}]}}}}}}},"format":{"property":"aggregations.over_time.buckets"}},"transform":[{"flatten":["lat_ranges.buckets"],"as":["range"]},{"calculate":"datum.range.key","as":"latency_band"},{"calculate":"datum.range.doc_count","as":"count"},{"filter":"datum.count > 0"}],"mark":{"type":"rect","cornerRadius":3},"encoding":{"x":{"field":"key","type":"temporal","axis":{"title":"Time","format":"%H:%M","labelAngle":-45}},"y":{"field":"latency_band","type":"ordinal","sort":["0-10 ms","10-50 ms","50-100 ms","100-300 ms","300-1000 ms","1s+"],"axis":{"title":"Latency"}},"color":{"field":"count","type":"quantitative","scale":{"scheme":"inferno","type":"sqrt"},"legend":{"title":"Request Count"}},"tooltip":[{"field":"key","type":"temporal","title":"Time"},{"field":"latency_band","type":"ordinal","title":"Latency"},{"field":"count","type":"quantitative","title":"Count"}]}}'

  # 2.3 Dependency Analysis (uses http.route and scripted avg duration)
  viz_line "viz-apm-dep" "APM - Dependency Analysis" \
    '{"$schema":"https://vega.github.io/schema/vega-lite/v5.json"'"$DC"',"title":"Avg Latency by Route","data":{"url":{"%context%":true,"index":"finance-traces*","body":{"size":0,"aggs":{"filtered":{"filter":{"exists":{"field":"attributes.http.route"}},"aggs":{"targets":{"terms":{"field":"attributes.http.route.keyword","size":20},"aggs":{"avg_lat":{"avg":{"script":{"source":"doc['\''endTime'\''].value.toInstant().toEpochMilli() - doc['\''startTime'\''].value.toInstant().toEpochMilli()","lang":"painless"}}}}}}}}}},"format":{"property":"aggregations.filtered.targets.buckets"}},"transform":[{"calculate":"datum.avg_lat.value != null && isFinite(datum.avg_lat.value) ? datum.avg_lat.value : 0","as":"avg_ms"}],"mark":{"type":"bar","cornerRadiusEnd":4},"encoding":{"y":{"field":"key","type":"nominal","axis":{"title":"HTTP Route"},"sort":"-x"},"x":{"field":"avg_ms","type":"quantitative","axis":{"title":"Avg Latency (ms)"}},"color":{"field":"avg_ms","type":"quantitative","scale":{"scheme":"redyellowgreen","reverse":true},"legend":{"title":"ms"}},"tooltip":[{"field":"key","type":"nominal","title":"Route"},{"field":"avg_ms","type":"quantitative","title":"Avg ms","format":".1f"}]}}'

  # ─── RELIABILITY DASHBOARD CHARTS ───

  # 3.1 Log Severity Distribution
  viz_line "viz-rel-logdist" "Reliability - Log Distribution" \
    '{"$schema":"https://vega.github.io/schema/vega-lite/v5.json"'"$DC"',"title":"Log Severity Distribution","data":{"url":{"%context%":true,"index":"finance-logs*","body":{"size":0,"aggs":{"severity":{"terms":{"field":"severity.keyword","size":10}}}}},"format":{"property":"aggregations.severity.buckets"}},"mark":{"type":"arc","innerRadius":50,"outerRadius":90,"cornerRadius":4},"encoding":{"theta":{"field":"doc_count","type":"quantitative"},"color":{"field":"key","type":"nominal","scale":{"domain":["INFO","WARN","ERROR","DEBUG","TRACE"],"range":["#54B399","#D6BF57","#E7664C","#6092C0","#888"]},"legend":{"title":"Severity"}},"tooltip":[{"field":"key","type":"nominal","title":"Severity"},{"field":"doc_count","type":"quantitative","title":"Count"}]}}'

  # 3.2 Log Volume Over Time (by severity)
  viz_line "viz-rel-errhm" "Reliability - Error Heatmap" \
    '{"$schema":"https://vega.github.io/schema/vega-lite/v5.json"'"$DC"',"title":"Log Volume Over Time","data":{"url":{"%context%":true,"index":"finance-logs*","body":{"size":0,"aggs":{"over_time":{"date_histogram":{"field":"@timestamp","fixed_interval":"1h","min_doc_count":1},"aggs":{"by_sev":{"terms":{"field":"severity.keyword","size":10}}}}}}},"format":{"property":"aggregations.over_time.buckets"}},"transform":[{"flatten":["by_sev.buckets"],"as":["sev_bucket"]},{"calculate":"datum.sev_bucket.key","as":"severity"},{"calculate":"datum.sev_bucket.doc_count","as":"count"}],"mark":{"type":"bar","cornerRadiusTopLeft":3,"cornerRadiusTopRight":3},"encoding":{"x":{"field":"key","type":"temporal","axis":{"title":"Time"}},"y":{"field":"count","type":"quantitative","stack":true,"axis":{"title":"Log Count"}},"color":{"field":"severity","type":"nominal","scale":{"domain":["INFO","WARN","ERROR","DEBUG","TRACE"],"range":["#54B399","#D6BF57","#E7664C","#6092C0","#888"]},"legend":{"title":"Severity"}},"tooltip":[{"field":"key","type":"temporal","title":"Time"},{"field":"severity","type":"nominal","title":"Severity"},{"field":"count","type":"quantitative","title":"Count"}]}}'

  # 3.3 Top Log Messages
  viz_line "viz-rel-toperr" "Reliability - Top Log Messages" \
    '{"$schema":"https://vega.github.io/schema/vega-lite/v5.json"'"$DC"',"title":"Top 10 Log Messages","data":{"url":{"%context%":true,"index":"finance-logs*","body":{"size":0,"aggs":{"top_msgs":{"terms":{"field":"body.keyword","size":10}}}}},"format":{"property":"aggregations.top_msgs.buckets"}},"mark":{"type":"bar","cornerRadiusEnd":4,"color":"#6092C0"},"encoding":{"y":{"field":"key","type":"nominal","axis":{"title":"Log Message","labelLimit":200},"sort":"-x"},"x":{"field":"doc_count","type":"quantitative","axis":{"title":"Occurrences"}},"tooltip":[{"field":"key","type":"nominal","title":"Message"},{"field":"doc_count","type":"quantitative","title":"Count"}]}}'
  # ─── METRICS DASHBOARD CHARTS ───

  # 4.1 CPU Usage Trend
  viz_line "viz-met-cpu" "Metrics - CPU Usage" \
    '{"$schema":"https://vega.github.io/schema/vega-lite/v5.json"'"$DC"',"title":"CPU Usage (%)","data":{"url":{"%context%":true,"index":"finance-metrics*","body":{"size":0,"aggs":{"filtered":{"filter":{"exists":{"field":"jvm.cpu.recent_utilization"}},"aggs":{"over_time":{"date_histogram":{"field":"@timestamp","fixed_interval":"1m"},"aggs":{"avg_cpu":{"avg":{"field":"jvm.cpu.recent_utilization"}}}}}}}}},"format":{"property":"aggregations.filtered.over_time.buckets"}},"transform":[{"calculate":"datum.avg_cpu.value != null && isFinite(datum.avg_cpu.value) ? datum.avg_cpu.value * 100 : 0","as":"cpu_pct"}],"mark":{"type":"area","line":true,"color":{"gradient":"linear","stops":[{"offset":0,"color":"rgba(84,179,153,0.3)"},{"offset":1,"color":"#54B399"}]}},"encoding":{"x":{"field":"key","type":"temporal","axis":{"title":"Time","format":"%H:%M"}},"y":{"field":"cpu_pct","type":"quantitative","axis":{"title":"CPU %"},"scale":{"zero":true}},"tooltip":[{"field":"key","type":"temporal","title":"Time"},{"field":"cpu_pct","type":"quantitative","title":"CPU %","format":".1f"}]}}'

  # 4.2 Memory Usage
  viz_line "viz-met-mem" "Metrics - Memory Usage" \
    '{"$schema":"https://vega.github.io/schema/vega-lite/v5.json"'"$DC"',"title":"JVM Heap Memory (MB)","data":{"url":{"%context%":true,"index":"finance-metrics*","body":{"size":0,"aggs":{"filtered":{"filter":{"bool":{"must":[{"exists":{"field":"jvm.memory.used"}},{"term":{"jvm.memory.type":"heap"}}]}},"aggs":{"over_time":{"date_histogram":{"field":"@timestamp","fixed_interval":"1m"},"aggs":{"total_mem":{"sum":{"field":"jvm.memory.used"}}}}}}}}},"format":{"property":"aggregations.filtered.over_time.buckets"}},"transform":[{"calculate":"datum.total_mem.value != null && isFinite(datum.total_mem.value) ? datum.total_mem.value / 1048576 : 0","as":"mem_mb"}],"mark":{"type":"area","line":true,"color":{"gradient":"linear","stops":[{"offset":0,"color":"rgba(96,146,192,0.3)"},{"offset":1,"color":"#6092C0"}]}},"encoding":{"x":{"field":"key","type":"temporal","axis":{"title":"Time","format":"%H:%M"}},"y":{"field":"mem_mb","type":"quantitative","axis":{"title":"Heap Memory (MB)"}},"tooltip":[{"field":"key","type":"temporal","title":"Time"},{"field":"mem_mb","type":"quantitative","title":"MB","format":".0f"}]}}'

  # 4.3 HTTP Requests
  viz_line "viz-met-reqcount" "Metrics - Request Count" \
    '{"$schema":"https://vega.github.io/schema/vega-lite/v5.json"'"$DC"',"title":"HTTP Requests per Minute","data":{"url":{"%context%":true,"index":"finance-metrics*","body":{"size":0,"aggs":{"filtered":{"filter":{"exists":{"field":"http.server.request.duration.counts"}},"aggs":{"over_time":{"date_histogram":{"field":"@timestamp","fixed_interval":"1m"}}}}}}},"format":{"property":"aggregations.filtered.over_time.buckets"}},"mark":{"type":"bar","cornerRadiusTopLeft":3,"cornerRadiusTopRight":3,"color":"#D6BF57"},"encoding":{"x":{"field":"key","type":"temporal","axis":{"title":"Time","format":"%H:%M"}},"y":{"field":"doc_count","type":"quantitative","axis":{"title":"Request Count"}},"tooltip":[{"field":"key","type":"temporal","title":"Time"},{"field":"doc_count","type":"quantitative","title":"Requests"}]}}'

  # 4.4 Error Rate Trend
  viz_line "viz-met-errrate" "Metrics - Error Rate Trend" \
    '{"$schema":"https://vega.github.io/schema/vega-lite/v5.json"'"$DC"',"title":"Error Rate Trend","data":{"url":{"%context%":true,"index":"finance-traces*","body":{"size":0,"aggs":{"over_time":{"date_histogram":{"field":"endTime","fixed_interval":"5m"},"aggs":{"total":{"value_count":{"field":"traceId"}},"errors":{"filter":{"term":{"status.code.keyword":"Error"}}}}}}}},"format":{"property":"aggregations.over_time.buckets"}},"transform":[{"calculate":"datum.total.value > 0 ? (datum.errors.doc_count / datum.total.value) * 100 : 0","as":"error_pct"}],"mark":{"type":"area","line":{"color":"#E7664C"},"color":{"gradient":"linear","stops":[{"offset":0,"color":"rgba(231,102,76,0.1)"},{"offset":1,"color":"rgba(231,102,76,0.4)"}]}},"encoding":{"x":{"field":"key","type":"temporal","axis":{"title":"Time","format":"%H:%M"}},"y":{"field":"error_pct","type":"quantitative","axis":{"title":"Error Rate %"}},"tooltip":[{"field":"key","type":"temporal","title":"Time"},{"field":"error_pct","type":"quantitative","title":"Error %","format":".2f"}]}}'

  # ─── DASHBOARDS ───
  dash_line "otel-exec-dashboard" "1. Executive Health Dashboard" "viz-exec-sr" "viz-exec-rpm" "viz-exec-uptime"
  dash_line "otel-apm-dashboard"  "2. APM & Performance Dashboard" "viz-apm-lat" "viz-apm-hm" "viz-apm-dep"
  dash_line "otel-rel-dashboard"  "3. Reliability & Logs Dashboard" "viz-rel-logdist" "viz-rel-errhm" "viz-rel-toperr"

  # 4th dashboard with 4 panels
  _panels4='[{"gridData":{"x":0,"y":0,"w":24,"h":15,"i":"1"},"panelIndex":"1","embeddableConfig":{},"version":"2.19.1","panelRefName":"panel_0"},{"gridData":{"x":24,"y":0,"w":24,"h":15,"i":"2"},"panelIndex":"2","embeddableConfig":{},"version":"2.19.1","panelRefName":"panel_1"},{"gridData":{"x":0,"y":15,"w":24,"h":15,"i":"3"},"panelIndex":"3","embeddableConfig":{},"version":"2.19.1","panelRefName":"panel_2"},{"gridData":{"x":24,"y":15,"w":24,"h":15,"i":"4"},"panelIndex":"4","embeddableConfig":{},"version":"2.19.1","panelRefName":"panel_3"}]'
  jq -nc --arg id "otel-met-dashboard" --arg title "4. Metrics & System Resources" --arg panels "$_panels4" \
    --arg v1 "viz-met-cpu" --arg v2 "viz-met-mem" --arg v3 "viz-met-reqcount" --arg v4 "viz-met-errrate" \
    '{"type":"dashboard","id":$id,"attributes":{"title":$title,"hits":0,"description":"","panelsJSON":$panels,"optionsJSON":"{\"useMargins\":true,\"hidePanelTitles\":false}","timeRestore":true,"timeFrom":"now-24h","timeTo":"now","kibanaSavedObjectMeta":{"searchSourceJSON":"{}"}},"references":[{"name":"panel_0","type":"visualization","id":$v1},{"name":"panel_1","type":"visualization","id":$v2},{"name":"panel_2","type":"visualization","id":$v3},{"name":"panel_3","type":"visualization","id":$v4}]}'

  # ─── INTEGRATION & DB DASHBOARD CHARTS ───

  # 5.1 DB Connection Pool Health (usage & pending over time)
  viz_line "viz-integ-dbpool" "Integration - DB Connection Pool" \
    '{"$schema":"https://vega.github.io/schema/vega-lite/v5.json"'"$DC"',"title":"DB Connection Pool Health","data":{"url":{"%context%":true,"index":"finance-metrics*","body":{"size":0,"aggs":{"over_time":{"date_histogram":{"field":"@timestamp","fixed_interval":"1m","min_doc_count":1},"aggs":{"avg_usage":{"avg":{"field":"db.client.connections.usage"}},"avg_pending":{"avg":{"field":"db.client.connections.pending_requests"}},"avg_max":{"avg":{"field":"db.client.connections.max"}}}}}}},"format":{"property":"aggregations.over_time.buckets"}},"transform":[{"calculate":"datum.key","as":"time"},{"calculate":"datum.avg_usage.value != null && isFinite(datum.avg_usage.value) ? datum.avg_usage.value : 0","as":"Active"},{"calculate":"datum.avg_pending.value != null && isFinite(datum.avg_pending.value) ? datum.avg_pending.value : 0","as":"Pending"},{"calculate":"datum.avg_max.value != null && isFinite(datum.avg_max.value) ? datum.avg_max.value : 0","as":"Max"},{"fold":["Active","Pending","Max"]}],"mark":{"type":"line","strokeWidth":2,"point":{"size":30}},"encoding":{"x":{"field":"time","type":"temporal","axis":{"title":"Time","format":"%H:%M"}},"y":{"field":"value","type":"quantitative","axis":{"title":"Connections"}},"color":{"field":"key","type":"nominal","scale":{"domain":["Active","Pending","Max"],"range":["#6092C0","#E7664C","#54B399"]},"legend":{"title":"Metric"}},"tooltip":[{"field":"time","type":"temporal","title":"Time"},{"field":"key","type":"nominal","title":"Metric"},{"field":"value","type":"quantitative","title":"Count","format":".0f"}]}}'

  # 5.2 JVM Thread State Distribution
  viz_line "viz-integ-threads" "Integration - Thread States" \
    '{"$schema":"https://vega.github.io/schema/vega-lite/v5.json"'"$DC"',"title":"JVM Thread State Distribution","data":{"url":{"%context%":true,"index":"finance-metrics*","body":{"size":0,"aggs":{"filtered":{"filter":{"exists":{"field":"jvm.thread.state"}},"aggs":{"states":{"terms":{"field":"jvm.thread.state","size":10},"aggs":{"total_threads":{"sum":{"field":"jvm.thread.count"}}}}}}}}},"format":{"property":"aggregations.filtered.states.buckets"}},"transform":[{"calculate":"datum.total_threads.value != null && isFinite(datum.total_threads.value) ? datum.total_threads.value : 0","as":"threads"}],"mark":{"type":"bar","cornerRadiusTopLeft":4,"cornerRadiusTopRight":4},"encoding":{"x":{"field":"key","type":"nominal","axis":{"title":"Thread State"}},"y":{"field":"threads","type":"quantitative","axis":{"title":"Thread Count"}},"color":{"field":"key","type":"nominal","scale":{"domain":["runnable","waiting","timed_waiting","blocked","new","terminated"],"range":["#54B399","#6092C0","#D6BF57","#E7664C","#888","#D32F2F"]},"legend":{"title":"State"}},"tooltip":[{"field":"key","type":"nominal","title":"State"},{"field":"threads","type":"quantitative","title":"Threads"}]}}'

  # 5.3 HTTP Server Request Duration Over Time
  viz_line "viz-integ-httplat" "Integration - HTTP Latency Trend" \
    '{"$schema":"https://vega.github.io/schema/vega-lite/v5.json"'"$DC"',"title":"HTTP Server Request Latency","data":{"url":{"%context%":true,"index":"finance-metrics*","body":{"size":0,"aggs":{"filtered":{"filter":{"exists":{"field":"http.server.request.duration.values"}},"aggs":{"over_time":{"date_histogram":{"field":"@timestamp","fixed_interval":"1m","min_doc_count":1},"aggs":{"avg_lat":{"avg":{"field":"http.server.request.duration.values"}}}}}}}}},"format":{"property":"aggregations.filtered.over_time.buckets"}},"transform":[{"calculate":"datum.avg_lat.value != null && isFinite(datum.avg_lat.value) ? datum.avg_lat.value * 1000 : 0","as":"latency_ms"}],"mark":{"type":"area","line":{"color":"#D6BF57"},"color":{"gradient":"linear","stops":[{"offset":0,"color":"rgba(214,191,87,0.15)"},{"offset":1,"color":"rgba(214,191,87,0.5)"}]}},"encoding":{"x":{"field":"key","type":"temporal","axis":{"title":"Time","format":"%H:%M"}},"y":{"field":"latency_ms","type":"quantitative","axis":{"title":"Avg Latency (ms)"}},"tooltip":[{"field":"key","type":"temporal","title":"Time"},{"field":"latency_ms","type":"quantitative","title":"Latency ms","format":".1f"}]}}'

  # 5th dashboard with 3 panels
  dash_line "otel-integ-dashboard" "5. Integration & DB Health" "viz-integ-dbpool" "viz-integ-threads" "viz-integ-httplat"

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
  echo "Kurulum tamamlandi! 5 Dashboard ve 16 Grafik basariyla olusturuldu."
  echo ""
  echo "   1. Executive Health Dashboard     - Genel saglik durumu"
  echo "   2. APM & Performance Dashboard    - Performans analizi"
  echo "   3. Reliability & Logs Dashboard   - Hata analizi"
  echo "   4. Metrics & System Resources     - Sistem kaynaklari"
  echo "   5. Integration & DB Health        - DB pool, thread, HTTP latency"
  echo ""
  echo "   http://opensearch-dashboards:5601/app/dashboards"
else
  echo "UYARI: Import sirasinda sorun olustu:"
  printf '%s' "$IMPORT_RESULT" | jq '.' 2>/dev/null || printf '%s\n' "$IMPORT_RESULT"
  exit 1
fi