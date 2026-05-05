#!/bin/sh
set -e

# Substitute env vars into the pipelines template since data-prepper does not
# expand ${VAR} placeholders inside pipelines.yaml at runtime.
sed -e "s|\${OPENSEARCH_USERNAME}|${OPENSEARCH_USERNAME:-admin}|g" \
    -e "s|\${OPENSEARCH_PASSWORD}|${OPENSEARCH_PASSWORD:-admin}|g" \
    /tmp/pipelines.template.yaml > /usr/share/data-prepper/pipelines/pipelines.yaml

exec /usr/share/data-prepper/bin/data-prepper
