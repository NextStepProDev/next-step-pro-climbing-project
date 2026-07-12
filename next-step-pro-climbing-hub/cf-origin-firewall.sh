#!/bin/bash
# Origin hardening, applied to DOCKER-USER (Docker bypasses the INPUT chain, so
# filtering container-published ports must live here, in the FORWARD path).
# Idempotent: flushes DOCKER-USER and re-applies. Runs after docker.service (unit).
# Cloudflare ranges change very rarely — review at https://www.cloudflare.com/ips-v4
set -e

CF="173.245.48.0/20 103.21.244.0/22 103.22.200.0/22 103.31.4.0/22 \
141.101.64.0/18 108.162.192.0/18 190.93.240.0/20 188.114.96.0/20 \
197.234.240.0/22 198.41.128.0/17 162.158.0.0/15 104.16.0.0/13 \
104.24.0.0/14 172.64.0.0/13 131.0.72.0/22"

# Wait for Docker to create the DOCKER-USER chain after boot.
for i in $(seq 1 30); do
  iptables -L DOCKER-USER -n >/dev/null 2>&1 && break
  sleep 2
done

# Reset our slice of the chain and rebuild.
iptables -F DOCKER-USER
iptables -A DOCKER-USER -m conntrack --ctstate RELATED,ESTABLISHED -j RETURN

# Allow container-originated egress (source = Docker bridge subnets). Inbound from
# the internet carries a public source IP, so the Cloudflare-only :443 filter below
# still applies to it. Without this, container egress to any :443 service (e.g. the
# Google OAuth token endpoint) is caught by the DROP below and hangs -> 504.
iptables -A DOCKER-USER -s 172.16.0.0/12 -j RETURN

# Port 443: allow only Cloudflare; drop everyone else.
for r in $CF; do
  iptables -A DOCKER-USER -s "$r" -p tcp -m tcp --dport 443 -j RETURN
done
iptables -A DOCKER-USER -p tcp -m tcp --dport 443 -j DROP

# Port 81 (NPM admin panel): block from the internet. Local / SSH-tunnel access
# still works — locally-originated traffic to a published port goes through the
# OUTPUT path, not FORWARD, so it never hits DOCKER-USER.
iptables -A DOCKER-USER -p tcp -m tcp --dport 81 -j DROP

# Port 80 is left open on purpose (only 301-redirects; keeps LE cert renewal safe).
