#!/usr/bin/env bash
#
# setup-swap.sh — 2 GB swap setup for the production server.
#
# Critical for a memory-constrained production machine: without swap the backend
# (Java/Spring) + PostgreSQL end up OOM. Creates /swapfile, sets swappiness 10
# and persists both across reboots (fstab + sysctl).
#
# Usage (once, on first deploy):
#   sudo bash setup-swap.sh
#
# The script is idempotent — it is safe to run again.

set -euo pipefail

SWAPFILE="/swapfile"
SWAP_SIZE="2G"
SWAPPINESS=10
SYSCTL_FILE="/etc/sysctl.d/99-swappiness.conf"

if [[ "${EUID}" -ne 0 ]]; then
  echo "This script requires root privileges. Run: sudo bash setup-swap.sh" >&2
  exit 1
fi

echo "==> Swap setup (${SWAP_SIZE} @ ${SWAPFILE}, swappiness ${SWAPPINESS})"

# 1. Create the swap file (if it does not exist yet)
if [[ -f "${SWAPFILE}" ]]; then
  echo "    ${SWAPFILE} already exists — skipping creation."
else
  echo "    Creating ${SWAPFILE} (${SWAP_SIZE})..."
  if ! fallocate -l "${SWAP_SIZE}" "${SWAPFILE}" 2>/dev/null; then
    # fallocate can fail on some filesystems (e.g. ext without support) — fall back to dd
    echo "    fallocate unavailable, using dd..."
    dd if=/dev/zero of="${SWAPFILE}" bs=1M count=2048 status=progress
  fi
  chmod 600 "${SWAPFILE}"
  mkswap "${SWAPFILE}"
fi

# 2. Permissions (just in case the file existed with loose permissions)
chmod 600 "${SWAPFILE}"

# 3. Enable swap (if not active)
if swapon --show=NAME --noheadings | grep -qx "${SWAPFILE}"; then
  echo "    Swap already active."
else
  echo "    Enabling swap..."
  swapon "${SWAPFILE}"
fi

# 4. Persist in /etc/fstab (survives reboot)
if grep -qE "^[[:space:]]*${SWAPFILE}[[:space:]]" /etc/fstab; then
  echo "    /etc/fstab entry already exists."
else
  echo "    Adding entry to /etc/fstab..."
  echo "${SWAPFILE} none swap sw 0 0" >> /etc/fstab
fi

# 5. Swappiness — persisted via sysctl.d + applied immediately
echo "    Setting vm.swappiness=${SWAPPINESS}..."
echo "vm.swappiness=${SWAPPINESS}" > "${SYSCTL_FILE}"
sysctl -w vm.swappiness="${SWAPPINESS}" >/dev/null

echo "==> Done. Current state:"
swapon --show
echo "    vm.swappiness = $(cat /proc/sys/vm/swappiness)"
free -h
