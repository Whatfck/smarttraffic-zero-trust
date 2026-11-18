#!/usr/bin/env bash
# =============================================================================
# Aprovisionamiento de una zona de seguridad de SmartTraffic (Zero Trust).
# Lo ejecuta Vagrant (o Ansible) como root dentro de cada VM:
#     provision.sh <vm-auth|vm-data|vm-ingest|vm-core|vm-app>
#
# Orden deliberado:
#   1. Se retira temporalmente el firewall de la zona (si existia) porque instalar
#      paquetes, descargar imagenes y compilar con Maven necesita Internet.
#   2. Se instala Docker Engine + Compose (repositorio oficial de Docker) y nftables.
#   3. Se compilan (Dockerfile multietapa) y arrancan los contenedores de la zona.
#   4. Se aplica el firewall "default deny" y se deja persistente para cada arranque.
# =============================================================================
set -euo pipefail

ZONE="${1:?Uso: provision.sh <nombre-de-la-vm>}"
PROJECT_DIR=/opt/smarttraffic
COMPOSE_FILE="$PROJECT_DIR/infra/compose/docker-compose.$ZONE.yml"
NFT_FILE="$PROJECT_DIR/infra/nftables/$ZONE.nft"
SECRETS_DIR="$PROJECT_DIR/.secrets"
SECRETS_FILE="$SECRETS_DIR/smarttraffic.env"

log() { echo "[smarttraffic][$ZONE] $*"; }

# --- 1. Retirar firewall de la zona durante el aprovisionamiento --------------
if command -v nft >/dev/null 2>&1; then
  nft delete table inet smarttraffic 2>/dev/null || true
fi

# --- 2. Docker Engine + Compose plugin + nftables -----------------------------
if ! command -v docker >/dev/null 2>&1 || ! docker compose version >/dev/null 2>&1; then
  log "Instalando Docker Engine, Compose y nftables..."
  export DEBIAN_FRONTEND=noninteractive
  apt-get update -qq
  apt-get install -y -qq ca-certificates curl gnupg nftables openssl netcat-openbsd >/dev/null
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  chmod a+r /etc/apt/keyrings/docker.asc
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
    > /etc/apt/sources.list.d/docker.list
  apt-get update -qq
  apt-get install -y -qq docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin >/dev/null
fi
systemctl enable --now docker >/dev/null
usermod -aG docker vagrant 2>/dev/null || true

# --- Secretos compartidos (se generan una sola vez, en la carpeta sincronizada) --
# Cada microservicio tiene su propio client_secret: el auth_server solo emite
# tokens a identidades conocidas. El archivo esta en .gitignore.
mkdir -p "$SECRETS_DIR"
if [ ! -s "$SECRETS_FILE" ]; then
  log "Generando secretos de la plataforma en .secrets/smarttraffic.env"
  rnd() { openssl rand -hex 24; }
  cat > "$SECRETS_FILE" <<EOF
# Generado automaticamente por provision.sh - NO subir a git
POSTGRES_PASSWORD=$(rnd)
SENSOR_CLIENT_SECRET=$(rnd)
PROCESSOR_CLIENT_SECRET=$(rnd)
ARCHIVER_CLIENT_SECRET=$(rnd)
DASHBOARD_CLIENT_SECRET=$(rnd)
ALERT_CLIENT_SECRET=$(rnd)
QUERY_CLIENT_SECRET=$(rnd)
KAFKA_BROKER_CLIENT_SECRET=$(rnd)
EOF
fi

# --- 3. Compilar y arrancar los contenedores de la zona -----------------------
if [ -f "$COMPOSE_FILE" ]; then
  log "Compilando y arrancando contenedores ($COMPOSE_FILE)..."
  cd "$PROJECT_DIR"
  docker compose --project-name smarttraffic --env-file "$SECRETS_FILE" \
    -f "$COMPOSE_FILE" up -d --build --remove-orphans
else
  log "AVISO: no existe $COMPOSE_FILE"
fi

# --- 4. Firewall Zero Trust (default deny) persistente -------------------------
log "Aplicando politica nftables default-deny..."
install -m 0644 "$NFT_FILE" /etc/nftables.conf
nft -f /etc/nftables.conf
systemctl enable nftables >/dev/null 2>&1 || true

log "Listo. Contenedores:"
docker compose --project-name smarttraffic --env-file "$SECRETS_FILE" -f "$COMPOSE_FILE" ps || true
