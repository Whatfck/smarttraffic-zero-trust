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

# --- vm-data: fijar la clave publica de la Autoridad para RabbitMQ ------------
# RabbitMQ solo acepta descargar JWKS por https; en su lugar se fija (pinning) la
# clave publica RS256 de vm-auth junto con su identificador (kid).
if [ "$ZONE" = "vm-data" ]; then
  KEY_DIR="$SECRETS_DIR/rabbitmq"
  mkdir -p "$KEY_DIR"
  AUTH=http://192.168.56.10:8081
  for attempt in $(seq 1 60); do
    curl -sf -m 5 "$AUTH/.well-known/signing-key.pem" -o "$KEY_DIR/signing-key.pem.tmp" && grep -q "BEGIN PUBLIC KEY" "$KEY_DIR/signing-key.pem.tmp" && break
    [ "$attempt" = 60 ] && { log "ERROR: vm-auth no publica su clave (levante vm-auth primero)"; exit 1; }
    log "Esperando la clave publica de vm-auth ($attempt/60)..."; sleep 5
  done
  mv -f "$KEY_DIR/signing-key.pem.tmp" "$KEY_DIR/signing-key.pem"
  KID=$(curl -sf -m 5 "$AUTH/.well-known/jwks.json" | grep -o '"kid":"[^"]*"' | cut -d'"' -f4)
  {
    echo "auth_oauth2.signing_keys.$KID = /etc/rabbitmq/keys/signing-key.pem"
    echo "auth_oauth2.default_key = $KID"
  } > "$KEY_DIR/30-signing-key.conf"
  chmod 644 "$KEY_DIR/signing-key.pem" "$KEY_DIR/30-signing-key.conf"
  log "Clave publica de vm-auth fijada en RabbitMQ (kid=$KID)"
fi

# --- Cache compartida de imagenes base (red lenta) ----------------------------
# La primera VM que descarga una imagen la guarda en .images/ (carpeta sincronizada)
# y las demas la cargan desde alli en vez de volver a bajarla de Internet.
IMAGES_DIR="$PROJECT_DIR/.images"
mkdir -p "$IMAGES_DIR" "$PROJECT_DIR/.mvn-repo"
cached_image() {
  local image="$1" file
  file="$IMAGES_DIR/$(echo "$image" | tr '/:' '__').tar"
  docker image inspect "$image" >/dev/null 2>&1 && return 0
  if [ -s "$file" ]; then
    log "Cargando $image desde cache local"
    docker load -i "$file" >/dev/null
  else
    log "Descargando $image"
    for attempt in 1 2 3 4 5; do
      docker pull -q "$image" >/dev/null && break
      [ "$attempt" = 5 ] && { log "ERROR: no se pudo descargar $image"; exit 1; }
      log "Reintentando descarga de $image ($attempt/5)..."; sleep 10
    done
    docker save -o "$file.$ZONE.tmp" "$image" && mv -f "$file.$ZONE.tmp" "$file"
  fi
}
if [ -f "$COMPOSE_FILE" ]; then
  if grep -q "dockerfile:" "$COMPOSE_FILE"; then
    cached_image maven:3.9.6-eclipse-temurin-17-alpine
    cached_image eclipse-temurin:17-jre-alpine
  fi
  for image in $(grep -E '^\s+image: [a-z]' "$COMPOSE_FILE" | awk '{print $2}' | grep -v '^smarttraffic/'); do
    cached_image "$image"
  done
fi

# --- 3. Compilar y arrancar los contenedores de la zona -----------------------
if [ -f "$COMPOSE_FILE" ]; then
  log "Compilando y arrancando contenedores ($COMPOSE_FILE)..."
  cd "$PROJECT_DIR"
  # Compilar de a una imagen: varias compilaciones Maven en paralelo agotan la RAM de la VM.
  COMPOSE=(docker compose --project-name smarttraffic --env-file "$SECRETS_FILE" -f "$COMPOSE_FILE")
  for service in $("${COMPOSE[@]}" config --services); do
    log "Preparando imagen de $service..."
    # no-op para servicios que solo usan image:. Se reintenta por cortes de red.
    for attempt in 1 2 3; do
      "${COMPOSE[@]}" build "$service" && break
      [ "$attempt" = 3 ] && { log "ERROR: no se pudo compilar $service"; exit 1; }
      log "Reintentando compilacion de $service ($attempt/3)..."; sleep 10
    done
  done
  docker compose --project-name smarttraffic --env-file "$SECRETS_FILE" \
    -f "$COMPOSE_FILE" up -d --remove-orphans
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
