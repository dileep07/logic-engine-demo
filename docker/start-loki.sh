#!/bin/sh

mkdir -p /tmp/loki/index \
         /tmp/loki/cache \
         /tmp/loki/chunks \
         /tmp/loki/compactor \
         /tmp/loki/wal

# hand off to Loki, pointing at our mounted config file
exec /usr/bin/loki -config.file=/etc/loki/local-config.yaml