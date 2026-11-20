# -*- mode: ruby -*-
# SmartTraffic - Zona Cero (Zero Trust)
#
# Infraestructura como Codigo (IaaC): este archivo define las 5 zonas de seguridad
# (una VM por zona) y las aprovisiona automaticamente con `vagrant up`.
#
# Todas las VMs comparten UNA sola red privada (host-only 192.168.56.0/24) para que
# puedan enrutarse entre si; el aislamiento NO lo da la red, lo dan las reglas
# nftables "default deny" de cada VM (infra/nftables/<vm>.nft). Eso es Zero Trust:
# estar en la misma red no otorga confianza.
#
# El orden importa: vm-auth (autoridad JWT) arranca primero porque los brokers de
# vm-data descargan de ella la clave publica (JWKS); luego vm-data y el resto.

ZONES = [
  # nombre        IP               RAM(MB) CPUs
  { name: "vm-auth",   ip: "192.168.56.10", memory: 1024, cpus: 1 },
  { name: "vm-data",   ip: "192.168.56.20", memory: 3072, cpus: 2 },
  { name: "vm-ingest", ip: "192.168.56.30", memory: 1024, cpus: 1 },
  { name: "vm-core",   ip: "192.168.56.40", memory: 1792, cpus: 2 },
  { name: "vm-app",    ip: "192.168.56.50", memory: 1792, cpus: 2 },
].freeze

Vagrant.configure("2") do |config|
  config.vm.box = "ubuntu/jammy64"
  config.vm.box_check_update = false
  config.vm.boot_timeout = 600

  # El repositorio se monta en cada VM para que Docker compile las imagenes alli.
  config.vm.synced_folder ".", "/opt/smarttraffic"

  ZONES.each do |zone|
    config.vm.define zone[:name] do |machine|
      machine.vm.hostname = zone[:name]
      machine.vm.network "private_network", ip: zone[:ip]

      machine.vm.provider "virtualbox" do |vb|
        vb.name = "smarttraffic-#{zone[:name]}"
        vb.memory = zone[:memory]
        vb.cpus = zone[:cpus]
        vb.customize ["modifyvm", :id, "--ioapic", "on"]
        # Reloj en UTC: Ubuntu asume que el RTC esta en UTC. Sin esto, en Windows la VM
        # arranca con la hora LOCAL tomada como UTC (5 h de desfase en Colombia) y los
        # JWT emitidos por vm-auth llegan "vencidos" a Kafka/RabbitMQ.
        vb.customize ["modifyvm", :id, "--rtcuseutc", "on"]
        # Sincronizar la hora del invitado con el anfitrion si se desvia mas de 10 s
        # (el firewall bloquea NTP, asi que la fuente de hora es el hipervisor).
        vb.customize ["guestproperty", "set", :id,
                      "/VirtualBox/GuestAdd/VBoxService/--timesync-set-threshold", "10000"]
      end

      # 1) Instala Docker + nftables, 2) compila y arranca los contenedores de la
      # zona, 3) aplica el firewall Zero Trust. Ver infra/scripts/provision.sh
      machine.vm.provision "shell",
        path: "infra/scripts/provision.sh",
        args: [zone[:name]]
    end
  end
end
