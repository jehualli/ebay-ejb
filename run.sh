#!/bin/sh
# Build and run the EJB edition on http://localhost:8084/
# (the servlet-only original stays on 8081 from ~/ebay-jsp)
set -e
cd "$(dirname "$0")"
docker build -t ebay-ejb .
docker rm -f ebay-ejb >/dev/null 2>&1 || true
docker run -d --name ebay-ejb -p 8084:8080 ebay-ejb
echo "eBay (EJB edition) starting on http://localhost:8084/   (guest / ebay)"
echo "Container comparison:  http://localhost:8084/contenedores"
echo "Logs:  docker logs -f ebay-ejb"
echo "Stop:  docker rm -f ebay-ejb"
