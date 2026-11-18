# Aprovisionamiento con Ansible (alternativa)

La via principal es `vagrant up`. Para maquinas que ya existen, este playbook hace lo mismo
ejecutando `infra/scripts/provision.sh <zona>` en cada host. El orden recomendado es
vm-auth, vm-data, vm-ingest, vm-core y vm-app; el script genera los secretos compartidos en
`.secrets/` la primera vez.

```bash
ansible-playbook -i inventory.ini site.yml --limit vm-auth
ansible-playbook -i inventory.ini site.yml --limit vm-data,vm-ingest,vm-core,vm-app
```
