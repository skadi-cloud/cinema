# Skadi Cloud

## Userful commands 

Access traefik dashboard:
```
kubectl port-forward --namespace kube-system service/traefik-admin 3000:8100
```

And open: http://localhost:3000/dashboard/#/