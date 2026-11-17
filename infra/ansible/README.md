# Aprovisionamiento

`Vagrantfile` crea las cinco zonas privadas y aplica el firewall de cada zona.
Para una instalación existente, `site.yml` instala Docker/nftables y aplica la
regla correspondiente al hostname:

```bash
ansible-playbook -i inventory site.yml
```

El inventario debe declarar `vm-auth`, `vm-data`, `vm-ingest`, `vm-core` y
`vm-app` con sus IPs privadas y credenciales SSH.
