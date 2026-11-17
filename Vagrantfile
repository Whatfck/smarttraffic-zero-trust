Vagrant.configure("2") do |config|
  config.vm.box = "ubuntu/jammy64"
  config.vm.synced_folder ".", "/opt/smarttraffic"

  zones = {
    "vm-auth" => "10.10.2.10",
    "vm-ingest" => "10.10.3.10",
    "vm-core" => "10.10.4.10",
    "vm-app" => "10.10.5.10",
    "vm-data" => "10.10.6.10"
  }

  zones.each do |name, ip|
    config.vm.define name do |machine|
      machine.vm.hostname = name
      machine.vm.network "private_network", ip: ip
      machine.vm.provider "virtualbox" do |vb|
        vb.memory = name == "vm-data" ? 3072 : 1536
        vb.cpus = name == "vm-data" ? 2 : 1
      end
      machine.vm.provision "shell", inline: <<-SHELL
        set -eu
        apt-get update -qq
        DEBIAN_FRONTEND=noninteractive apt-get install -y -qq nftables docker.io docker-compose-plugin
        systemctl enable --now docker
        install -d -m 0755 /etc/nftables.d
        if [ -f /opt/smarttraffic/infra/nftables/#{name}.nft ]; then
          cp /opt/smarttraffic/infra/nftables/#{name}.nft /etc/nftables.d/smarttraffic.nft
          nft -f /etc/nftables.d/smarttraffic.nft
        fi
      SHELL
    end
  end
end
