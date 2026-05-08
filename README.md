# 🗂️ OCI FileStorage CRUD API — POC Completa (Python + Spring Boot + Mock SFTP)

> Tres implementaciones de microservicios REST sobre **OCI File Storage (NFS)** desplegados en **Oracle Kubernetes Engine (OKE)** y administrados desde un **Bastion Host**: uno en **Python/FastAPI**, otro en **Java/Spring Boot** y un tercero en **Spring Boot + Mock SFTP** que se comporta como intermediario transparente para clientes que ya hablan SFTP — el archivo aterriza en el mismo File Storage sin que el cliente note la diferencia.

---

[![Python](https://img.shields.io/badge/Python-3.12-3776AB?style=for-the-badge&logo=python&logoColor=white)](https://www.python.org/)
[![FastAPI](https://img.shields.io/badge/FastAPI-0.115-009688?style=for-the-badge&logo=fastapi&logoColor=white)](https://fastapi.tiangolo.com/)
[![Java](https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2.5-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)](https://spring.io/projects/spring-boot)
[![JSch](https://img.shields.io/badge/JSch-mwiede_fork-orange?style=for-the-badge)](https://github.com/mwiede/jsch)
[![atmoz/sftp](https://img.shields.io/badge/SFTP-atmoz%2Fsftp-blue?style=for-the-badge)](https://hub.docker.com/r/atmoz/sftp)
[![Kubernetes](https://img.shields.io/badge/Kubernetes-OKE-326CE5?style=for-the-badge&logo=kubernetes&logoColor=white)](https://docs.oracle.com/en-us/iaas/Content/ContEng/home.htm)
[![OCI](https://img.shields.io/badge/Oracle_Cloud-Infrastructure-F80000?style=for-the-badge&logo=oracle&logoColor=white)](https://cloud.oracle.com/)
[![Docker](https://img.shields.io/badge/Docker-Enabled-2496ED?style=for-the-badge&logo=docker&logoColor=white)](https://www.docker.com/)

---

## 📐 Arquitecturas

La POC contiene **dos arquitecturas distintas** que comparten el mismo `pvc-filestore` sobre OCI File Storage:

### Arquitectura 1 — Acceso REST directo (Python + Spring Boot)

Los microservicios FastAPI y Spring Boot exponen un CRUD REST y leen/escriben directamente sobre el volumen NFS. Es la implementación base de la POC.

```
                        ┌──────────────────────────────────────────────────────┐
                        │                  OCI Cloud                           │
                        │                                                      │
    Tu máquina          │    ┌──────────────┐      ┌──────────────────────┐    │
    local/internet ─────┼───►│ Load Balancer│─────►│   OKE Cluster        │    │
                        │    │ (sn-lb)      │      │  ┌────────────────┐  │    │
                        │    └──────────────┘      │  │ Pod: FastAPI   │  │    │
                        │                          │  │ (2 réplicas)   │  │    │
                        │                          │  └───────┬────────┘  │    │
                        │                          │  ┌───────┴────────┐  │    │
    Tú (admin) ─────────┼──► Bastion Host ─────►   │  │ Pod: SpringBoot│  │    │
                        │    (SSH + kubectl)       │  │ (2 réplicas)   │  │    │
                        │    (sn-bastion)          │  └───────┬────────┘  │    │
                        │                          └──────────┼───────────┘    │
                        │                                     │                │
                        │                         ┌───────────▼───────────┐    │
                        │                         │  OCI File Storage     │    │
                        │                         │  (NFS Mount Target)   │    │
                        │                         │  /filestore           │    │
                        │                         └───────────────────────┘    │
                        └──────────────────────────────────────────────────────┘
```

> 💡 El mismo `pvc-filestore` es montado por el microservicio Python y el microservicio Spring Boot simultáneamente. Un archivo subido por uno es visible inmediatamente por el otro, gracias al modo de acceso `ReadWriteMany` de NFS.

### Arquitectura 2 — Mock SFTP intermedio (Spring Boot + atmoz/sftp)

Pensada para clientes existentes que ya hablan SFTP contra un servidor on-premise y migran a OCI sin tocar su código. El microservicio Spring Boot recibe el request HTTP y lo reenvía vía SFTP a un contenedor `atmoz/sftp` corriendo dentro del cluster, cuyo directorio `/home/sftpuser/upload` está montado sobre el mismo `pvc-filestore`. Para el cliente todo se ve como un SFTP convencional, pero los archivos terminan en el OCI File Storage.

```
                        ┌──────────────────────────────────────────────────────┐
                        │                  OCI Cloud                           │
                        │                                                      │
    Tu máquina          │    ┌──────────────┐      ┌──────────────────────┐    │
    local/internet ─────┼───►│ Load Balancer│─────►│   OKE Cluster        │    │
                        │    │ (sn-lb)      │      │  ┌────────────────┐  │    │
                        │    └──────────────┘      │  │ Pod: SpringBoot│  │    │
                        │                          │  │  Mock SFTP API │  │    │
                        │                          │  └───────┬────────┘  │    │
                        │                          │          │ SFTP :22  │    │
                        │                          │  ┌───────▼────────┐  │    │
                        │                          │  │ Pod: atmoz/sftp│  │    │
                        │                          │  │ ClusterIP only │  │    │
                        │                          │  └───────┬────────┘  │    │
                        │                          └──────────┼───────────┘    │
                        │                                     │                │
                        │                         ┌───────────▼───────────┐    │
                        │                         │  OCI File Storage     │    │
                        │                         │  (NFS Mount Target)   │    │
                        │                         │  /filestore           │    │
                        │                         └───────────────────────┘    │
                        └──────────────────────────────────────────────────────┘
```

> 💡 El cliente (Postman u otro microservicio) nunca ve al SFTP — solo habla HTTP contra el Spring Boot. Internamente el Spring Boot abre una sesión SSH/SFTP contra `sftp-server-svc:22` (ClusterIP, no expuesto al exterior) y deja el archivo en `/home/sftpuser/upload`, que está montado sobre el File Storage. El resultado neto: un archivo subido por SFTP es visible desde los pods Python y Spring Boot directos por el mismo PVC.

---

## 📋 Tabla de Contenidos

- [Prerrequisitos y Variables](#-fase-0--prerrequisitos-y-planificación)
- [Fase 1 — Red (VCN y Subnets)](#-fase-1--crear-la-red)
- [Fase 2 — OCI File Storage](#-fase-2--crear-el-oci-file-storage)
- [Fase 3 — Cluster OKE](#-fase-3--crear-el-cluster-oke)
- [Fase 4 — Bastion Host](#-fase-4--crear-el-bastion-host)
- [Fase 5 — Código Python](#-fase-5--el-microservicio-en-python)
- [Fase 5B — Código Spring Boot](#-fase-5b--el-microservicio-en-spring-boot)
- [Por qué NO usamos Multipart](#-por-qué-no-usamos-multipart)
- [Fase 5C — Spring Boot + Mock SFTP](#-fase-5c--el-microservicio-spring-boot--mock-sftp)
- [Error de permisos en /upload (Mock SFTP)](#-error-de-permisos-en-el-sftp--permission-denied)
- [Fase 6 — OCIR (Imagen Docker)](#-fase-6--publicar-la-imagen-en-ocir)
- [Fase 7 — Manifiestos K8s](#-fase-7--manifiestos-de-kubernetes)
- [Fase 8 — Despliegue](#-fase-8--desplegar-todo-en-oke)
- [Fase 9 — Pruebas](#-fase-9--verificar-y-probar-la-api)
- [Fase 10 — Troubleshooting](#-fase-10--troubleshooting)
- [Colección de Postman](#-colección-de-postman)
- [Resumen de Componentes](#-resumen-de-componentes)
- [Resultados POC](#-resultados-poc)

---

## 🗺️ Fase 0 — Prerrequisitos y planificación

Antes de tocar la consola, ten listos estos valores. Los irás completando a medida que avanzas:

```bash
TENANCY_OCID             = ocid1.tenancy.oc1..xxxxx
OBJECT_STORAGE_NAMESPACE = ixfl...
COMPARTMENT_OCID         = ocid1.compartment.oc1..xxxxx
USER_OCID                = ocid1.user.oc1..xxxxxxx
REGION                   = us-ashburn-1  (o la tuya, ej: us-ashburn-1)
VCN_OCID                 = (lo obtienes en Fase 1)
SUBNET_OKE_OCID          = (lo obtienes en Fase 1)
SUBNET_LB_OCID           = (lo obtienes en Fase 1)
SUBNET_BASTION           = (lo obtienes en Fase 1)
MT_OCID                  = (Mount Target, Fase 2)
EXPORT_PATH              = /filestore
STORAGE_PATH             = /mnt/filestore
MT_IP                    = (Mount Target, Fase 2)
OKE_CLUSTER_OCID         = (lo obtienes en Fase 3)
OCIR_REPO                = (lo obtienes en Fase 6)
IMAGE_TAG                = v1.0
```

---

## 🌐 Fase 1 — Crear la Red

### 1.1 — Crear la VCN

1. Ve a **Networking → Virtual Cloud Networks → Create VCN**
2. Configura:
   - **Name:** `vcn-oke`
   - **IPv4 CIDR:** `10.0.0.0/16`
   - ✅ Selecciona **"VCN with Internet Connectivity"** (crea IGW y Route Table automáticamente)
3. Clic en **Create VCN**

---

### 1.2 — Crear las Subnets

Necesitas **3 subnets** con los siguientes rangos:

| Nombre | CIDR | Tipo | Uso |
|--------|------|------|-----|
| `sn-oke-nodes` | `10.0.1.0/24` | **Privada** | Nodos del cluster K8s |
| `sn-lb` | `10.0.2.0/24` | **Pública** | Load Balancer externo |
| `sn-bastion-host` | `10.0.3.0/24` | **Pública** | Acceso SSH al cluster |

Para cada subnet: **Networking → VCN → Create Subnet** con los valores de la tabla.

- `sn-oke-nodes` → Route Table: la privada con NAT Gateway (ver paso 1.3)
- `sn-lb` y `sn-bastion-host` → Route Table: la que tiene Internet Gateway

---

### 1.3 — NAT Gateway (para nodos privados)

Los nodos privados necesitan salida a internet (para descargar imágenes, etc.):

1. En tu VCN → **NAT Gateways → Create NAT Gateway**
   - **Name:** `nat-gw-oke`
2. Crea una Route Table privada:
   - **Name:** `rt-private-nodes`
   - Regla: Destino `0.0.0.0/0` → Target: `nat-gw-poc`
3. Edita `sn-oke-nodes` y asígnale esta route table

---
### 1.4 — Internet Gateway (para subnets públicas)

Los servicios públicos requieren conexión a internet:

**sn-lb**
```
Usuario en internet → Internet Gateway → Load Balancer → Pods
```
**sn-bastion-host**
```
Tú desde tu casa → Internet Gateway → Bastion Host (SSH)
```
1. En tu VCN → **Internet Gateways → Create Internet Gateway**
   - **Name:** `ig-oke-vcn`
2. Crea una Route Table pública:
   - **Name:** `rt-public`
3. Edita `sn-lb` y `sn-bastion-host` y asígnales esta route table

---
### 1.4 — Security Lists

#### `sn-oke-nodes` — Ingress Rules

| Source CIDR | Protocolo | Puerto | Descripción |
|-------------|-----------|--------|-------------|
| `10.0.0.0/16` | TCP | All | Tráfico interno VCN |
| `10.0.2.0/24` | TCP | 30000–32767 | NodePort desde LB |
| `10.0.1.0/24` | TCP | 2048–2050 | NFS |
| `10.0.1.0/24` | UDP | 2048 | NFS |
| `10.0.1.0/24` | TCP | 111 | Portmapper |
| `10.0.1.0/24` | UDP | 111 | Portmapper |

#### `sn-oke-nodes` — Egress Rules
| Source CIDR | Protocolo | Puerto | Descripción |
|-------------|-----------|--------|-------------|
| `10.0.1.0/24` | TCP | 2048–2050 | NFS |
| `10.0.1.0/24` | UDP | 2048 | NFS |
| `10.0.1.0/24` | TCP | 111 | Portmapper |
| `10.0.1.0/24` | UDP | 111 | Portmapper |

#### `sn-lb` — Ingress Rules

| Source CIDR | Protocolo | Puerto | Descripción |
|-------------|-----------|--------|-------------|
| `0.0.0.0/0` | TCP | 80 | HTTP público |
| `0.0.0.0/0` | TCP | 443 | HTTPS público |

#### `sn-bastion-host` — Ingress Rules

| Source CIDR | Protocolo | Puerto | Descripción |
|-------------|-----------|--------|-------------|
| `0.0.0.0/0` | TCP | 22 | SSH (restringe a tu IP en producción) |

> ⚠️ En producción, reemplaza `0.0.0.0/0` del SSH por tu IP específica.

---

## 🗄️ Fase 2 — Crear el OCI File Storage

### 2.1 — Crear el File System

1. **Storage → File Storage → File Systems → Create File System**
2. Configura:
   - **File System Name:** `fs-poc-files`
   - **Availability Domain:** elige uno (ej: `AD-1`)
   - **Compartment:** el tuyo

### 2.2 — Crear el Mount Target

En el mismo panel:
- **Name:** `mt-poc`
- **VCN:** `vcn-oke`
- **Subnet:** `sn-oke-nodes` ← misma red que los pods
- Clic en **Create**

> 📌 Anota la **IP del Mount Target** (visible en la sección del Mount Target, algo como `10.0.1.XX`). La usarás en el PersistentVolume de K8s.

### 2.3 — Crear el Export

1. Entra al File System → **Exports → Create Export**
   - **Export Path:** `/filestore`
   - **Mount Target:** `mt-poc`
2. Clic en **Create Export**

---

## ☸️ Fase 3 — Crear el Cluster OKE

### 3.1 — Crear el cluster (Custom Create)

1. **Developer Services → Kubernetes Clusters (OKE) → Create Cluster**
2. Elige **Custom Create** para usar las subnets ya creadas
3. Configura:

| Campo | Valor |
|-------|-------|
| Name | `oke-cluster-1` |
| Kubernetes Version | La más reciente disponible (ej: `v1.30.x`) |
| VCN | `vcn-oke` |
| LB Subnets | `sn-lb` |
| API Endpoint Subnet | `sn-oke-nodes` |
| Assign public IP al API | ❌ NO (accedemos por Bastion) |

### 3.2 — Configurar el Node Pools

| Campo | Valor |
|-------|-------|
| Name | `high-mem` |
| Shape | `VM.Standard.E4.Flex` (2 OCPUs, 16 GB RAM) |
| Image | Oracle Linux 8 (compatible con la versión de K8s) |
| OCPUs | `3` |
| RAM | `32`GB |
| Nodes | `1` |
| Subnet | `sn-oke-nodes` |
| SSH Key | Agrega tu llave pública |

| Campo | Valor |
|-------|-------|
| Name | `platform` |
| Shape | `VM.Standard.E4.Flex` (2 OCPUs, 16 GB RAM) |
| Image | Oracle Linux 8 (compatible con la versión de K8s) |
| OCPUs | `2` |
| RAM | `24`GB |
| Nodes | `1` |
| Subnet | `sn-oke-nodes` |
| SSH Key | Agrega tu llave pública |

Clic en **Create Cluster** y espera ~10 minutos hasta que el estado sea **Active**.

---

## 🖥️ Fase 4 — Crear el Bastion Host

### 4.1 — Crear la instancia

1. **Compute → Instances → Create Instance**

| Campo | Valor |
|-------|-------|
| Name | `oke-bastion-host` |
| Image | Oracle Linux 8 |
| Shape | `VM.Standard.E2.1.Micro` (Free Tier ✅) |
| VCN | `vcn-oke` |
| Subnet | `sn-bastion-host` |
| IPv4 pública | ✅ Asignar |
| SSH Key | Pega tu llave pública |

> 📌 Anota la **IP pública** del Bastion.

---

### 4.2 — Instalar herramientas en el Bastion

```bash
# Conectarse al Bastion
ssh -i ~/.ssh/tu_llave_privada opc@<IP_PUBLICA_BASTION>

# Actualizar sistema
sudo dnf update -y

# Instalar OCI CLI
bash -c "$(curl -L https://raw.githubusercontent.com/oracle/oci-cli/master/scripts/install/install.sh)"
source ~/.bashrc

# Instalar kubectl
curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
chmod +x kubectl
sudo mv kubectl /usr/local/bin/

# Verificar instalaciones
kubectl version --client
oci --version
```

---

### 4.3 — Configurar OCI CLI

```bash
oci setup config
```

El wizard pedirá:
- **User OCID** → Tu perfil en OCI Console → copiar OCID
- **Tenancy OCID** → Administration → Tenancy Details
- **Region** → ej: `us-ashburn-1`
- **API Key** → el CLI genera un par. Copia la **llave pública** que muestra

Luego sube la llave pública a OCI:
1. **User → API Keys → Add API Key**
2. Pega la llave pública generada por el CLI

---

### 4.4 — Obtener el kubeconfig

```bash
OKE_CLUSTER_OCID="ocid1.cluster.oc1..xxxxx"   # reemplaza con el tuyo

oci ce cluster create-kubeconfig \
  --cluster-id $OKE_CLUSTER_OCID \
  --file $HOME/.kube/config \
  --region us-ashburn-1 \
  --token-version 2.0.0 \
  --kube-endpoint PRIVATE_ENDPOINT

# Verificar conexión — deberías ver 2 nodos en estado Ready
kubectl get nodes
```

---

## 🐍 Fase 5 — El Microservicio en Python

### Estructura general del proyecto

```
OCI-OKE-FileStorage-Service/
├── app/
│   ├── python/                ← Microservicio FastAPI
│   │   ├── main.py
│   │   ├── Dockerfile
│   │   ├── requirements.txt
│   │   └── oke-manifests/
│   │       ├── namespace.yaml
│   │       ├── pv.yaml
│   │       ├── pvc.yaml
│   │       ├── deployment.yaml
│   │       ├── service.yaml
│   │       └── secret-example.yaml
│   └── springboot/            ← Microservicio Spring Boot
│       ├── pom.xml
│       ├── Dockerfile
│       ├── src/main/java/com/poc/filestore/
│       │   ├── FileStoreApplication.java
│       │   ├── controller/FileController.java
│       │   └── service/FileService.java
│       ├── src/main/resources/application.yml
│       └── oke-manifests/
│           ├── deployment.yaml
│           └── service.yaml
├── Postman/
│   └── POC_OKE-FileStorage.json
└── results/
```

> Los manifiestos `namespace.yaml`, `pv.yaml` y `pvc.yaml` son **compartidos** — solo se crean una vez (desde `app/python/oke-manifests/`) y son reutilizados por el microservicio Spring Boot.

---

### `requirements.txt`

Define las 4 dependencias del microservicio. FastAPI es el framework web que expone los endpoints REST. Uvicorn es el servidor ASGI que corre la aplicación (el equivalente a Gunicorn pero async). python-multipart es obligatorio para que FastAPI pueda recibir archivos via formularios (multipart/form-data). aiofiles permite leer y escribir archivos de forma asíncrona sin bloquear el event loop, lo que hace que las subidas y descargas sean eficientes.

---

### `app/main.py`

Es el corazón del microservicio. Define una API REST con 7 endpoints que operan sobre un directorio del sistema de archivos (que en producción es el NFS montado). La ruta base de almacenamiento se lee de la variable de entorno STORAGE_PATH, lo que permite configurarla sin tocar el código. Cada operación CRUD está mapeada a su método HTTP correcto: POST para crear, GET para leer, PUT para actualizar y DELETE para borrar. Todas las operaciones de I/O son asíncronas gracias a aiofiles. El endpoint /health existe exclusivamente para que Kubernetes pueda verificar que el pod está vivo y listo para recibir tráfico.

---

### `Dockerfile`

Construye la imagen del contenedor en base a python:3.12-slim para mantenerla liviana. Instala las dependencias de Python desde requirements.txt, copia el código de la aplicación, crea el directorio /mnt/filestore que luego será reemplazado por el volumen NFS, y arranca el servidor Uvicorn escuchando en el puerto 8000 en todas las interfaces. La variable de entorno STORAGE_PATH está definida aquí con su valor por defecto.

---

### Endpoints disponibles

| Método | Ruta | Descripción |
|--------|------|-------------|
| `GET` | `/health` | Health check del servicio |
| `GET` | `/files` | Listar todos los archivos |
| `POST` | `/files/{filename}` | Subir un archivo nuevo |
| `GET` | `/files/{filename}` | Descargar un archivo |
| `PUT` | `/files/{filename}` | Actualizar un archivo existente |
| `DELETE` | `/files/{filename}` | Eliminar un archivo |
| `GET` | `/files/{filename}/info` | Ver metadata de un archivo |
| `GET` | `/docs` | Swagger UI interactivo 📖 |

---

## 🍃 Fase 5B — El Microservicio en Spring Boot

Microservicio equivalente al de Python, escrito con **Java 17 + Spring Boot 3.2.5**, desplegado en el mismo cluster OKE y namespace, montando el mismo `pvc-filestore`. Un archivo subido por uno es visible inmediatamente por el otro.

### Estructura del proyecto

```
app/springboot/
├── pom.xml
├── Dockerfile
├── src/
│   └── main/
│       ├── java/com/poc/filestore/
│       │   ├── FileStoreApplication.java
│       │   ├── controller/FileController.java
│       │   └── service/FileService.java
│       └── resources/application.yml
└── oke-manifests/
    ├── deployment.yaml
    └── service.yaml
```

---

### `pom.xml`

Define el proyecto Maven con `spring-boot-starter-parent` 3.2.5 y Java 17. Las dependencias clave son `spring-boot-starter-web` (servidor REST embebido sobre Tomcat), `spring-boot-starter-actuator` (expone `/actuator/health` para los probes de Kubernetes) y `lombok` (reduce el boilerplate). El plugin de Spring Boot empaqueta todo en un JAR ejecutable autocontenido.

---

### `application.yml`

Archivo de configuración principal. Define el puerto del servidor (`8080`), lee la ruta de almacenamiento desde la variable de entorno `STORAGE_PATH` (con fallback a `/mnt/filestore`) y habilita el endpoint de health del actuator con detalles completos para que Kubernetes lo consulte.

---

### `FileStoreApplication.java`

Punto de entrada del microservicio. Una sola clase con `@SpringBootApplication` que arranca el contenedor de Spring y deja el servidor Tomcat escuchando en el puerto configurado.

---

### `FileController.java`

Define los endpoints REST bajo el prefijo `/files`. Implementa los 6 verbos del CRUD: `GET /files` (listar), `POST /files/{filename}` (crear), `GET /files/{filename}` (descargar), `PUT /files/{filename}` (actualizar), `DELETE /files/{filename}` (eliminar) y `GET /files/{filename}/info` (metadata). **Importante:** los endpoints de subida y actualización leen el body del request directamente como `InputStream` en vez de usar `MultipartFile` — esta decisión se explica en la siguiente sección.

---

### `FileService.java`

Capa de servicio que encapsula toda la lógica de I/O contra el File Storage. Inicializa el directorio raíz (`STORAGE_PATH`) en el `@PostConstruct`, y expone métodos para listar, leer, eliminar y obtener metadata de archivos usando la API moderna de `java.nio.file`. Centraliza el manejo de errores con `ResponseStatusException` para devolver códigos HTTP apropiados.

---

### Endpoints disponibles

| Método | Ruta | Descripción | Body |
|--------|------|-------------|------|
| `GET` | `/actuator/health` | Health check | — |
| `GET` | `/files` | Listar todos los archivos | — |
| `POST` | `/files/{filename}` | Subir archivo nuevo | `form-data` (campo `file`) |
| `GET` | `/files/{filename}` | Descargar archivo | — |
| `PUT` | `/files/{filename}` | Actualizar archivo existente | `form-data` (campo `file`) |
| `DELETE` | `/files/{filename}` | Eliminar archivo | — |
| `GET` | `/files/{filename}/info` | Ver metadata del archivo | — |

---

## ⚠️ Por qué NO usamos Multipart (en Spring Boot)

> 📌 **Aclaración importante:** "no usamos Multipart" se refiere a que en Spring Boot **no usamos el binding `MultipartFile` ni el `MultipartResolver`** del framework. El cliente (Postman) sigue enviando `multipart/form-data` exactamente igual que para el microservicio Python — lo que cambia es **cómo lo procesa el servidor**: en vez de pedirle a Spring que parsee el multipart, el controller lee el body crudo del request como `InputStream` y lo escribe directo al disco.

Esta es una decisión de diseño importante en el microservicio Spring Boot que generó varios problemas durante el desarrollo y vale la pena documentarla.

### Qué es Multipart

Multipart es un formato HTTP que permite enviar múltiples partes en un solo request. Cada parte tiene sus propios headers y body. Se ve así por dentro:

```
--boundary123abc
Content-Disposition: form-data; name="file"; filename="foto.png"
Content-Type: image/png

[bytes del archivo aquí]
--boundary123abc--
```

### Para qué sirve

Multipart existe para casos donde necesitas enviar un archivo junto con otros datos en el mismo request — por ejemplo, subir una foto junto con su título y descripción en un formulario HTML:

```
--boundary
Content-Disposition: form-data; name="titulo"

Mi foto de vacaciones
--boundary
Content-Disposition: form-data; name="file"; filename="foto.png"
Content-Type: image/png

[bytes]
--boundary--
```

### El problema que causó en este proyecto

Cuando se intentó usar la vía clásica de Spring (`@RequestParam("file") MultipartFile file`), aparecieron errores intermitentes. El Load Balancer de OCI actúa como proxy HTTP y al reenviar las peticiones a los pods **modifica o pierde el header `Content-Type`**, incluyendo el `boundary` que el parser de multipart necesita para saber dónde termina una parte y empieza la siguiente.

Sin el boundary el `MultipartResolver` de Spring no puede parsear el body y arroja:

```
InvalidContentTypeException: the request doesn't contain a multipart/form-data
or multipart/mixed stream, content type header is null
```

### La solución que aplicamos

En vez de depender del `MultipartResolver` de Spring, el `FileController.java` recibe directamente el `HttpServletRequest` y lee el body crudo con `request.getInputStream()`, escribiéndolo al disco sin pedirle al framework que lo interprete:

```java
try (InputStream is = request.getInputStream()) {
    Files.copy(is, dest);
}
```

Así el cliente puede seguir mandando `multipart/form-data` (igual que para FastAPI) sin que importe si el LB altera o no el `Content-Type` — el controller no lo necesita para guardar los bytes.

### Comparación

| | `MultipartFile` de Spring | `HttpServletRequest` + `InputStream` |
|---|---|---|
| Necesita `Content-Type` con boundary intacto | ✅ Sí (frágil tras el LB) | ❌ No (robusto) |
| Parsea automáticamente las "parts" | ✅ Sí | ❌ No (no hace falta) |
| Útil cuando hay varios campos en el form | ✅ Sí | ⚠️ Habría que parsear a mano |
| Para esta API (un solo archivo, nombre en la URL) | ❌ Frágil | ✅ Correcto |

> 💡 Ambos microservicios (Python y Spring Boot) reciben `multipart/form-data` desde el cliente. La diferencia está en el servidor: FastAPI usa su parser interno y Spring Boot **bypasea** el suyo leyendo el `InputStream` directamente.

---

### `Dockerfile` (Spring Boot)

Construye la imagen usando un **multi-stage build**: el primer stage usa `maven:3.9.6-eclipse-temurin-17` para compilar el JAR; el segundo stage usa solo `eclipse-temurin:17-jre` y copia únicamente el JAR compilado. La imagen final no incluye Maven, código fuente ni dependencias de compilación — es mucho más liviana y segura. Define `STORAGE_PATH=/mnt/filestore` y `JAVA_OPTS` con límites de heap (256m–512m), expone el puerto 8080 y arranca con `java -jar`.

---

## 🔐 Fase 5C — El Microservicio Spring Boot + Mock SFTP

Tercera implementación de la POC. Mantiene la misma fachada REST que las anteriores, pero internamente actúa como **intermediario SFTP**: recibe HTTP, abre una sesión SSH/SFTP contra un contenedor `atmoz/sftp` corriendo en el mismo cluster, y deposita el archivo en el directorio `upload` que el SFTP tiene montado sobre el `pvc-filestore`. El cliente sigue creyendo que está hablando con un SFTP "real" — el File Storage queda escondido detrás.

### Propósito

El cliente tiene microservicios on-premise que envían archivos a un servidor SFTP. Migrar a OCI implicaría reemplazar ese SFTP por un File Storage, pero **cambiar todos los clientes que ya hablan SFTP es costoso y riesgoso**. La solución: un Mock SFTP que habla SFTP por fuera y por dentro está montado sobre el File Storage. Los clientes existentes no necesitan ningún cambio.

### Estructura del proyecto

```
app/springboot-mockSFTP-filestorage/
├── pom.xml
├── Dockerfile
├── oke-manifests/
│   ├── sftp-deployment.yaml        ← contenedor atmoz/sftp
│   ├── sftp-service.yaml           ← ClusterIP interno para el SFTP
│   ├── deployment.yaml             ← Spring Boot que llama al SFTP
│   └── service.yaml                ← LoadBalancer público de la API
└── src/main/
    ├── java/com/poc/filestore/
    │   ├── FileStoreApplication.java
    │   ├── config/SftpConfig.java
    │   ├── controller/FileController.java
    │   └── service/SftpService.java
    └── resources/application.yaml
```

> Reutiliza los manifiestos compartidos (`namespace`, `pv`, `pvc`) ubicados en `general-oke-manifests/`.

---

### `pom.xml` (Mock SFTP)

Define el proyecto Maven con Spring Boot 3.2.5 y Java 17. Suma una dependencia clave: el cliente JSch del fork **`com.github.mwiede:jsch`** en vez del original `com.jcraft:jsch`. Más detalle en la siguiente subsección.

---

### `application.yaml` (Mock SFTP)

Archivo de configuración. Define el puerto 8080, expone el endpoint `/actuator/health` y agrega un bloque `sftp:` con las credenciales y datos de conexión al contenedor SFTP (`host`, `port`, `username`, `password`, `remote-dir`). Cada valor se puede sobrescribir con variables de entorno (`SFTP_HOST`, `SFTP_PORT`, `SFTP_USERNAME`, `SFTP_PASSWORD`, `SFTP_REMOTE_DIR`) — útil para inyectar las credenciales desde el `Deployment` de Kubernetes sin tocar el JAR.

---

### `FileStoreApplication.java` (Mock SFTP)

Punto de entrada del microservicio. Igual que en las otras implementaciones, una clase con `@SpringBootApplication` que arranca el contenedor Spring y deja el servidor Tomcat escuchando en el puerto configurado.

---

### `SftpConfig.java`

Clase `@ConfigurationProperties(prefix = "sftp")` que mapea el bloque `sftp:` del `application.yaml` a un bean fuertemente tipado. Centraliza host, puerto, usuario, contraseña y `remoteDir` para que el resto del código no tenga que leer propiedades sueltas — se inyecta como dependencia donde haga falta.

---

### `SftpService.java`

Capa de servicio que encapsula toda la interacción con el SFTP. Expone cuatro operaciones — `uploadFile`, `downloadFile`, `deleteFile` y `listFiles` — y cada una abre una sesión JSch contra el contenedor SFTP, navega al `remoteDir` (`/upload`), realiza la operación y cierra la conexión. Desactiva `StrictHostKeyChecking` porque el host es interno al cluster y su clave cambia en cada despliegue. Es el componente que sustituye al `FileService` directo de la implementación anterior — la API REST hacia afuera es la misma, pero los bytes ya no van al disco local sino a través de la red SFTP.

---

### `FileController.java` (Mock SFTP)

Controlador REST con el mismo prefijo `/files` que las otras implementaciones, pero delegando todo el I/O al `SftpService` en vez de a un servicio de filesystem directo. Implementa `GET /files` (listar), `POST /files/{filename}` (subir, body `binary`), `GET /files/{filename}` (descargar), `DELETE /files/{filename}` (eliminar) y un `GET /files/health`. Es importante el cambio de body en el `POST`: a diferencia de las implementaciones anteriores que aceptaban `multipart/form-data`, aquí el endpoint lee el body crudo (`request.getInputStream()`) y lo reenvía tal cual al SFTP — el LB de OCI igualmente le quita el `boundary` al multipart, así que el body en Postman debe ser `binary`.

---

### `Dockerfile` (Mock SFTP)

Mismo patrón multi-stage del Spring Boot anterior: stage de build con `maven:3.9.6-eclipse-temurin-17` que produce el JAR, stage runtime con `eclipse-temurin:17-jre` que copia solo el JAR. Expone el puerto 8080.

---

### `oke-manifests/sftp-deployment.yaml`

Despliega el contenedor **`atmoz/sftp`** (1 réplica) que actúa como servidor SFTP dentro del cluster. Crea el usuario `sftpuser` con password `sftppass123` y le asigna el directorio `/home/sftpuser/upload`, que se monta desde el `pvc-filestore` (el mismo PVC que usan los demás microservicios). Incluye un **`initContainer`** con `busybox` que corre `chmod 777 /home/sftpuser/upload` antes de arrancar el SFTP — sin esto el upload falla con "Permission denied", ver la siguiente sección.

---

### `oke-manifests/sftp-service.yaml`

Service `sftp-server-svc` de tipo **ClusterIP** que expone el SFTP **solo dentro del cluster** en el puerto 22. No tiene IP pública: el único cliente legítimo es el pod Spring Boot. Esto es importante por seguridad — el SFTP no debería ser accesible desde internet.

---

### `oke-manifests/deployment.yaml` (Spring Boot Mock SFTP)

Deployment `filestore-api-java` (2 réplicas) con la imagen Spring Boot del Mock SFTP. Inyecta las credenciales SFTP por variables de entorno (`SFTP_HOST=sftp-server-svc`, `SFTP_PORT=22`, `SFTP_USERNAME=sftpuser`, `SFTP_PASSWORD=sftppass123`, `SFTP_REMOTE_DIR=/upload`) — el host es el nombre DNS del Service del SFTP dentro del cluster. Probes apuntando a `/actuator/health`, igual que en la implementación Spring Boot directa.

---

### `oke-manifests/service.yaml` (Spring Boot Mock SFTP)

Service `filestore-api-java2-svc` de tipo **LoadBalancer** que expone el Spring Boot al exterior con su propia IP pública en el puerto 80 → 8080. Es el único punto de entrada externo de esta arquitectura — todo lo demás (SFTP, NFS) queda interno.

---

### Por qué `mwiede/jsch` y no `jcraft/jsch`

El fork original `com.jcraft:jsch` está **abandonado desde 2016** y no soporta los algoritmos de clave SSH modernos que usa `atmoz/sftp` por defecto (ed25519, ecdsa, rsa con SHA-2). Si se intenta conectar con el JSch viejo, JSch falla con `Algorithm negotiation fail` antes incluso de pedir credenciales.

El fork **`com.github.mwiede:jsch`** es un drop-in replacement (mismas clases, mismo paquete `com.jcraft.jsch.*`, mismo API), está mantenido activamente y soporta todos los algoritmos modernos. La única diferencia es el `groupId` en el `pom.xml`. Esta POC usa la versión `0.2.17`.

---

## ⚠️ Error de permisos en el SFTP — Permission denied

Este fue el error más importante durante el despliegue de la arquitectura Mock SFTP y vale documentarlo en detalle.

### El error

Al intentar subir un archivo desde Postman, los logs del Spring Boot mostraban que la sesión SSH se establecía y el canal SFTP se abría correctamente, pero al ejecutar el `put` fallaba con:

```
ERROR : Error subiendo archivo al SFTP: Permission denied
com.jcraft.jsch.SftpException: Permission denied
```

### La causa

El directorio `/home/sftpuser/upload` dentro del contenedor SFTP está montado desde el `pvc-filestore`. Ese PVC es el mismo File Storage NFS que ya usan los microservicios Python y Spring Boot directo, donde los archivos son propiedad de **`root`** (UID 0).

Cuando `atmoz/sftp` crea el usuario `sftpuser` (UID por defecto 1001), ese usuario **no tiene permisos de escritura** sobre un directorio que pertenece a `root`. La conexión SSH funciona porque solo necesita validar credenciales del usuario; pero al intentar escribir el archivo en `/home/sftpuser/upload`, el filesystem responde con `EACCES`, que JSch traduce a `Permission denied`.

```
Contenedor SFTP
├── /home/sftpuser/          ← propiedad de sftpuser ✅
│   └── upload/              ← montado desde PVC, propiedad de root ❌
│       └── archivo.pdf      ← sftpuser no puede escribir aquí
```

### La solución — initContainer

Se agrega un **`initContainer`** en `sftp-deployment.yaml` que corre **antes** de que arranque el contenedor SFTP. El initContainer corre como `root` por defecto (a diferencia del SFTP, que corre como `sftpuser`), monta el mismo volumen y ejecuta `chmod 777 /home/sftpuser/upload`. Cuando el contenedor SFTP arranca, el directorio ya tiene permisos abiertos y `sftpuser` puede escribir sin problemas.

El flujo de arranque del pod queda así:

```
1. initContainer: fix-permissions (busybox, root)
   └── chmod 777 /home/sftpuser/upload
   └── termina exitosamente
       ↓
2. Container: sftp-server (atmoz/sftp, sftpuser)
   └── /home/sftpuser/upload ya tiene permisos 777
   └── sftpuser puede leer y escribir ✅
```

> ⚠️ `chmod 777` es deliberadamente permisivo y está bien para una POC. En producción debería usarse `chmod 755` con el UID/GID correcto del usuario SFTP, idealmente alineando el `fsGroup` del pod con el ownership del NFS (o usando un `securityContext` con `runAsUser` específico) para evitar abrir el directorio a "todos".

---

## 📦 Fase 6 — Publicar la Imagen en OCIR

### 6.1 — Crear repositorios en OCIR

1. **Developer Services → Container Registry → Create Repository**
   - Crea **un** repositorio privados:
     - `filestore-api`
   - **Access:** Private
2. Anota el **namespace** de OCIR (algo como `axyz1234abc`, visible en la pantalla).

### 6.2 — Generar Auth Token

1. **User Settings → Auth Tokens → Generate Token**
   - **Description:** `authtkn`
2. Copia y guarda el token (solo se muestra una vez)

### 6.3 — Build y Push de las imágenes

```bash
# Variables — ajusta con tus valores reales
REGION="us-ashburn-1"
NAMESPACE="axyz1234abc"
TAG="v1.0"

# Login en OCIR (un único login sirve para ambas imágenes)
docker login ${REGION}.ocir.io \
  -u "${NAMESPACE}/<tu_username_oci>" \
  -p "<tu_auth_token>"

# ── Imagen Python ───────────────────────────────────
cd app/python
docker build -t ${REGION}.ocir.io/${NAMESPACE}/filestore-api:${TAG} .
docker push ${REGION}.ocir.io/${NAMESPACE}/filestore-api:${TAG}

# ── Imagen Spring Boot ──────────────────────────────
cd ../springboot
mvn clean package -DskipTests
docker build -t ${REGION}.ocir.io/${NAMESPACE}/filestore-api-java:${TAG} .
docker push ${REGION}.ocir.io/${NAMESPACE}/filestore-api-java:${TAG}
```

> 💡 **Prerrequisitos para Spring Boot en el Bastion:** instalar JDK 17 (`sudo dnf install java-17-openjdk-devel -y`) y Maven (`sudo dnf install maven -y`), y exportar `JAVA_HOME=/usr/lib/jvm/java-17-openjdk`.

---

## ☸️ Fase 7 — Manifiestos de Kubernetes

Crea estos archivos en `filestore-api/k8s/`.

### `namespace.yaml`

Crea el namespace poc-filestore en Kubernetes. Es un espacio de nombres aislado donde vivirán todos los recursos de esta POC (pods, servicios, volúmenes, secrets). Sirve para no mezclar estos recursos con otros que pueda haber en el cluster.

---

### `pv.yaml`

> ⚠️ Reemplaza `<MT_IP>` con la IP real del Mount Target (anotada en Fase 2)

Crea un PersistentVolume que representa el File Storage de OCI dentro de Kubernetes. Le dice al cluster que existe un servidor NFS en la IP del Mount Target con el path /filestore, con capacidad de 50Gi. El modo de acceso ReadWriteMany es clave: permite que múltiples pods simultáneamente lean y escriban en el mismo volumen, algo que solo es posible gracias a NFS. La política Retain significa que si el PVC es eliminado, los datos no se borran automáticamente.

---

### `pvc.yaml`

Crea un PersistentVolumeClaim, que es la "solicitud" de un volumen por parte de la aplicación. Se enlaza estáticamente al PV anterior mediante volumeName: pv-filestore. Los pods no montan el PV directamente, siempre lo hacen a través del PVC, lo que desacopla la aplicación de los detalles de infraestructura del almacenamiento.

---

### Secret OCIR (comando directo en el Bastion)

```bash
kubectl create secret docker-registry ocir-secret \
  --docker-server=us-ashburn-1.ocir.io \
  --docker-username="<NAMESPACE>/<tu_username>" \
  --docker-password="<tu_auth_token>" \
  --docker-email="<tu_email>" \
  -n poc-filestore
```

---

### `deployment.yaml`

> ⚠️ Reemplaza la imagen con la URL real de tu OCIR

Le dice a Kubernetes cómo correr el microservicio. Define 2 réplicas del pod para alta disponibilidad. Especifica la imagen a usar desde OCIR (el registry privado de OCI), monta el PVC en /mnt/filestore dentro del contenedor, y establece límites de CPU y memoria para que ningún pod consuma recursos excesivos. Los liveness y readiness probes apuntan a /health: el liveness reinicia el pod si la app deja de responder, y el readiness evita que el Load Balancer envíe tráfico a un pod que aún no está listo.

---

### `service.yaml`

> ⚠️ Reemplaza `<SUBNET_LB_OCID>` con el OCID real de `sn-lb`

Expone el Deployment al mundo exterior creando un Load Balancer de OCI automáticamente gracias al tipo LoadBalancer. Las anotaciones le indican a OCI que cree un LB flexible con capacidad entre 10 y 100 Mbps, y que lo coloque en la subnet pública (sn-lb). El servicio recibe tráfico en el puerto 80 y lo redirige al puerto 8000 de los pods. Una vez creado, OCI asigna una IP pública que es la URL de entrada a toda la aplicación.

---

### Manifiestos del microservicio Spring Boot

Estos archivos viven en `app/springboot/oke-manifests/` y reutilizan el `namespace`, `pv` y `pvc` ya creados para Python.

#### `deployment.yaml` (Spring Boot)

> ⚠️ Reemplaza la imagen con la URL real de tu OCIR (`filestore-api-java:v1.0`)

Define el Deployment `filestore-api-java` con 2 réplicas. Usa la imagen Java pusheada a OCIR, expone el puerto 8080 (puerto por defecto de Spring Boot), monta el mismo `pvc-filestore` en `/mnt/filestore` y define la variable `STORAGE_PATH`. Los probes apuntan a `/actuator/health` (en vez de `/health` como en Python) porque Spring Boot expone los health checks bajo el prefijo del actuator. Los límites de memoria se ajustan a la JVM (request 512Mi / limit 768Mi) ya que Java consume más RAM que Python.

#### `service.yaml` (Spring Boot)

> ⚠️ Reemplaza `<SUBNET_LB_OCID>` con el OCID real de `sn-lb`

Crea un Service `filestore-api-java-svc` de tipo LoadBalancer que aprovisiona un **segundo Load Balancer en OCI** independiente del de Python. Esto da a cada microservicio su propia IP pública para poder probarlos por separado. El servicio recibe tráfico en el puerto 80 y lo redirige al puerto 8080 de los pods Spring Boot.

---

## 🚀 Fase 8 — Desplegar todo en OKE

### Copiar manifiestos al Bastion

```bash
# Desde tu máquina local
scp -i ~/.ssh/tu_llave -r filestore-api/k8s/ opc@<IP_BASTION>:~/k8s/

# Conectarse al Bastion
ssh -i ~/.ssh/tu_llave opc@<IP_BASTION>
```

### Aplicar todos los manifiestos

```bash
# ── Recursos compartidos ──────────────────────────────
# 1. Crear namespace
kubectl apply -f ~/k8s/python/namespace.yaml

# 2. Crear secret para OCIR
kubectl create secret docker-registry ocir-secret \
  --docker-server=us-ashburn-1.ocir.io \
  --docker-username="<NAMESPACE>/<username>" \
  --docker-password="<auth_token>" \
  --docker-email="<email>" \
  -n poc-filestore

# 3. Crear el volumen persistente y el claim (compartidos por ambos micros)
kubectl apply -f ~/k8s/python/pv.yaml
kubectl apply -f ~/k8s/python/pvc.yaml

# 4. Verificar que el PVC quede en estado Bound
kubectl get pvc -n poc-filestore
# STATUS debe ser "Bound" antes de continuar

# ── Microservicio Python ──────────────────────────────
kubectl apply -f ~/k8s/python/deployment.yaml
kubectl apply -f ~/k8s/python/service.yaml

# ── Microservicio Spring Boot ─────────────────────────
kubectl apply -f ~/k8s/springboot/deployment.yaml
kubectl apply -f ~/k8s/springboot/service.yaml

# ── Monitoreo ─────────────────────────────────────────
kubectl rollout status deployment/filestore-api -n poc-filestore
kubectl rollout status deployment/filestore-api-java -n poc-filestore
kubectl get pods -n poc-filestore
kubectl get svc -n poc-filestore
# Espera ~2-3 minutos hasta que aparezcan las EXTERNAL-IP de ambos LBs
```

> 💡 **Redesplegar tras cambios en Spring Boot:**
> ```bash
> cd app/springboot && \
>   mvn clean package -DskipTests && \
>   docker build -t ${REGION}.ocir.io/${NAMESPACE}/filestore-api-java:${TAG} . && \
>   docker push ${REGION}.ocir.io/${NAMESPACE}/filestore-api-java:${TAG} && \
>   kubectl rollout restart deployment/filestore-api-java -n poc-filestore
> ```

---

## ✅ Fase 9 — Verificar y Probar la API

Una vez que `kubectl get svc` muestre la `EXTERNAL-IP`:

```bash
LB_IP="<EXTERNAL_IP>"

# ── Health Check ──────────────────────────────────
curl http://$LB_IP/health

# ── Listar archivos (vacío al inicio) ────────────
curl http://$LB_IP/files

# ── Subir un archivo ─────────────────────────────
curl -X POST http://$LB_IP/files/documento.pdf \
  -F "file=@/ruta/local/documento.pdf"

# ── Listar archivos (aparece el subido) ──────────
curl http://$LB_IP/files

# ── Ver metadata del archivo ─────────────────────
curl http://$LB_IP/files/documento.pdf/info

# ── Descargar el archivo ─────────────────────────
curl -O http://$LB_IP/files/documento.pdf

# ── Actualizar archivo ───────────────────────────
curl -X PUT http://$LB_IP/files/documento.pdf \
  -F "file=@/ruta/local/documento_v2.pdf"

# ── Eliminar archivo ─────────────────────────────
curl -X DELETE http://$LB_IP/files/documento.pdf
```

> 📖 También puedes explorar la API visualmente en:
> ```
> http://<EXTERNAL_IP>/docs   ←  Swagger UI (generado automáticamente por FastAPI)
> ```

---

## 🔍 Fase 10 — Troubleshooting

### Ver logs de los pods

```bash
kubectl logs -f deployment/filestore-api -n poc-filestore
```

### Inspeccionar el montaje NFS dentro del pod

```bash
# Obtener nombre del pod
kubectl get pods -n poc-filestore

# Entrar al contenedor
kubectl exec -it <nombre-del-pod> -n poc-filestore -- /bin/bash

# Dentro del pod: verificar el mount
ls /mnt/filestore
df -h /mnt/filestore
```

### Ver eventos de un recurso (útil si el PVC no hace Bound)

```bash
kubectl describe pvc pvc-filestore -n poc-filestore
kubectl describe pod <nombre-pod> -n poc-filestore
kubectl describe svc filestore-api-svc -n poc-filestore
```

### Verificar NFS desde un nodo worker (SSH via Bastion como jump host)

```bash
# Conectar al nodo usando el Bastion como salto
ssh -J opc@<IP_BASTION> opc@<IP_NODO_PRIVADO>

# Dentro del nodo: verificar que el Mount Target responde
showmount -e <MT_IP>
# Debe mostrar: /filestore  *
```

### Reiniciar el deployment si es necesario

```bash
kubectl rollout restart deployment/filestore-api -n poc-filestore
kubectl rollout status deployment/filestore-api -n poc-filestore
```

### Problemas comunes

| Síntoma | Causa probable | Solución |
|---------|---------------|----------|
| PVC en `Pending` | IP del MT incorrecta o puerto NFS bloqueado | Verificar Security List y la IP del Mount Target |
| Pod en `ImagePullBackOff` | Secret OCIR incorrecto o imagen no existe | Verificar el secret y que la imagen fue pusheada |
| LB sin `EXTERNAL-IP` | OCID de sn-lb incorrecto en el annotation | Revisar el `service.yaml` y re-aplicar |
| Error 500 al subir | `/mnt/filestore` sin permisos de escritura | Verificar el Export del File Storage |
| SSH al Bastion falla | Puerto 22 bloqueado en Security List | Agregar regla de ingress en `sn-bastion` |
| `InvalidContentTypeException` (Spring Boot) | Se intentó parsear el body con `MultipartFile` pero el LB removió el `Content-Type`/boundary | Recibir `HttpServletRequest` y leer `request.getInputStream()` directamente, sin depender del `MultipartResolver` |
| `ImagePullBackOff` (Spring Boot) | Secret OCIR expirado o imagen Java no pusheada | Recrear el secret y verificar el push |
| Pod Spring Boot en `0/1 Running` | Spring tarda ~15s en arrancar | Esperar — el `readinessProbe` lo maneja |

---

## 📮 Colección de Postman

En la carpeta `Postman/` del repositorio se incluye el archivo `POC_OKE-FileStorage.json` con todas las peticiones pre-configuradas para probar **ambos microservicios** (Python y Spring Boot) sobre el cluster desplegado.

### ¿Qué es y cómo funciona?

Una **colección de Postman** es un archivo JSON exportable que agrupa peticiones HTTP organizadas por carpetas. Al importarla en Postman, obtienes todos los requests listos para ejecutar — con su método, URL, headers y body ya rellenos — sin tener que escribirlos a mano.

La colección incluye dos sub-carpetas:

- **`Python`** — peticiones contra el microservicio FastAPI (`/health`, `/files`, `/files/{filename}`, etc.). Las subidas usan `multipart/form-data` (campo `file`).
- **`SpringBoot`** — peticiones contra el microservicio Java (`/actuator/health`, `/files`, `/files/{filename}`, etc.). Las subidas también usan `multipart/form-data` (campo `file`) — exactamente igual que en Python. La diferencia está del lado del servidor: el controller Spring Boot lee el body como `InputStream` en vez de usar el `MultipartResolver`, ver [Por qué NO usamos Multipart](#-por-qué-no-usamos-multipart).

### Cómo usarla

1. Abre Postman → **File → Import**
2. Arrastra el archivo `Postman/POC_OKE-FileStorage.json`
3. Reemplaza la IP `132.226.48.218` (la del Load Balancer usado durante la POC) por la `EXTERNAL-IP` de **tu** Load Balancer — usa la IP del LB de Python para los requests Python, y la del LB de Spring Boot para los requests SpringBoot (`kubectl get svc -n poc-filestore`).
4. Para los `POST`/`PUT` de subida, en la pestaña **Body** selecciona el archivo local que vas a enviar.
5. Ejecuta cada request con **Send** y revisa la respuesta.

> 💡 Tanto en la carpeta **Python** como en **SpringBoot** los requests de subida usan **`form-data`** con un campo `file` que contiene el archivo a subir. No hay que cambiar a `binary`.

---

## 📊 Resumen de Componentes

| Componente | Tipo | Propósito |
|---|---|---|
| `vcn-poc-oke` | VCN | Red principal `10.0.0.0/16` |
| `sn-oke-nodes` | Subnet Privada `10.0.1.0/24` | Nodos K8s y File Storage |
| `sn-lb` | Subnet Pública `10.0.2.0/24` | Load Balancer externo |
| `sn-bastion` | Subnet Pública `10.0.3.0/24` | Acceso SSH al cluster |
| `nat-gw-poc` | NAT Gateway | Salida a internet para nodos privados |
| `fs-poc-files` | OCI File Storage | Almacenamiento NFS persistente |
| `mt-poc` | Mount Target | Punto de montaje NFS |
| `bastion-poc` | Compute VM (Free Tier) | Acceso kubectl al cluster |
| `oke-poc-cluster` | OKE Cluster | Orquestación de contenedores |
| `nodepool-poc` | Node Pool (2 nodos) | Capacidad de cómputo |
| `poc/filestore-api` | OCIR Repository | Registry privado de imagen Docker |
| `pv-filestore` | PersistentVolume K8s | Mapeo NFS en Kubernetes |
| `pvc-filestore` | PersistentVolumeClaim K8s | Claim del volumen para los pods |
| `filestore-api` | Deployment K8s (2 réplicas) | Microservicio FastAPI (Python) |
| `filestore-api-svc` | LoadBalancer Service K8s | Exposición pública HTTP del micro Python |
| `filestore-api-java` | Deployment K8s (2 réplicas) | Microservicio Spring Boot (Java) — directo a NFS o vía Mock SFTP según despliegue |
| `filestore-api-java-svc` | LoadBalancer Service K8s | Exposición pública HTTP del micro Spring Boot directo |
| `filestore-api-java2-svc` | LoadBalancer Service K8s | Exposición pública HTTP del micro Spring Boot Mock SFTP |
| `sftp-server` | Deployment K8s (1 réplica) | Contenedor `atmoz/sftp` con `initContainer` de permisos |
| `sftp-server-svc` | ClusterIP Service K8s | SFTP interno solo accesible desde dentro del cluster |

---

## 🧪 Resultados POC

A continuación se muestran capturas de las pruebas realizadas contra la API desplegada en OKE (a través de la IP pública del Load Balancer `132.226.48.218`), ejecutadas desde Postman recorriendo todo el ciclo CRUD del microservicio.

### 1. Health Check — `GET /health`

![Health Check](results/1.png)

Verificación de que el pod está vivo y el volumen NFS está correctamente montado. La API responde **200 OK** con `{"status": "ok", "storage_path": "/mnt/filestore"}`, confirmando que el `STORAGE_PATH` definido por la variable de entorno apunta al File Storage de OCI.

---

### 2. Listado inicial — `GET /files`

![Listado vacío](results/2.png)

Primer listado de archivos sobre el File Storage recién montado. La respuesta **200 OK** devuelve `{"files": [], "total": 0}`, lo que confirma que el volumen está accesible desde el pod pero aún no contiene ningún archivo.

---

### 3. Subida de archivo — `POST /files/documento.pdf`

![Subida exitosa](results/3.png)

Carga de un archivo (`HV_SIMON_MARQUEZ.pdf`) usando `multipart/form-data` y guardado en el NFS bajo el nombre `documento.pdf`. La API responde **201 Created** con metadata del archivo: tamaño (`591273` bytes) y timestamp de creación.

---

### 4. Validación de duplicados — `POST` sobre archivo existente

![Conflicto 409](results/4.png)

Al intentar volver a subir un archivo con el mismo nombre, la API responde **409 Conflict** con el mensaje `"El archivo 'documento.pdf' ya existe. Usa PUT para actualizarlo."`. Esto demuestra que el endpoint distingue correctamente entre creación (POST) y actualización (PUT).

---

### 5. Subida de un segundo archivo — `POST /files/documento1.pdf`

![Segunda subida](results/5.png)

Subida de un segundo archivo distinto (`Apphasia - Modelo de Dominio (ER) (1).png`) renombrado como `documento1.pdf`. Respuesta **201 Created** confirmando que múltiples archivos pueden coexistir en el mismo volumen NFS compartido entre las réplicas del Deployment.

---

### 6. Metadata de un archivo — `GET /files/documento.pdf/info`

![Metadata del archivo](results/6.png)

Consulta de la información detallada del archivo: nombre, tamaño en bytes, fecha de creación, fecha de modificación y ruta absoluta dentro del NFS (`/mnt/filestore/documento.pdf`). Útil para inspeccionar el estado del archivo sin necesidad de descargarlo.

---

### 7. Descarga de archivo — `GET /files/documento.pdf`

![Descarga del archivo](results/7.png)

Descarga del contenido binario del PDF directamente desde el File Storage. La respuesta **200 OK** devuelve el archivo crudo, demostrando que el flujo de lectura asíncrono con `aiofiles` funciona correctamente sobre el volumen NFS.

---

### 8. Actualización de archivo — `PUT /files/documento.pdf`

![Actualización exitosa](results/8.png)

Reemplazo del contenido de `documento.pdf` con un nuevo archivo (`IMG-20190409-WA0002.jpg`). La API responde **200 OK** con `"Archivo actualizado exitosamente"` y el nuevo `size_bytes` y `updated_at`, confirmando que el PUT sobreescribe correctamente el contenido en el NFS.

---

### 9. Eliminación de archivo — `DELETE /files/documento1.pdf`

![Eliminación exitosa](results/9.png)

Borrado del archivo `documento1.pdf` desde el File Storage. La respuesta **200 OK** con `"Archivo 'documento1.pdf' eliminado exitosamente"` cierra el ciclo CRUD completo (Create, Read, Update, Delete) operando contra el almacenamiento NFS persistente expuesto en OCI.

---

> ✅ **Conclusión de la POC:** todos los endpoints CRUD operan correctamente sobre el OCI File Storage montado vía NFS dentro del cluster OKE, accesible públicamente a través del Load Balancer gestionado automáticamente por OCI.

---

> 📌 **Nota sobre los resultados mostrados:** las capturas anteriores corresponden al **microservicio Python (FastAPI)**. El microservicio **Spring Boot** expone los mismos endpoints CRUD y se comporta de forma equivalente sobre el mismo `pvc-filestore` compartido. Si quieres reproducir las pruebas contra la versión Spring Boot, importa la **[colección de Postman](#-colección-de-postman)** incluida en `Postman/POC_OKE-FileStorage.json` — contiene los requests pre-configurados para ambos microservicios y solo necesitas reemplazar la IP del Load Balancer por la tuya.

> 📌 **Nota sobre el microservicio Spring Boot + Mock SFTP:** la tercera implementación ([Fase 5C](#-fase-5c--el-microservicio-spring-boot--mock-sftp)) expone el **mismo CRUD** sobre los mismos endpoints `/files`, así que el comportamiento observable desde Postman es equivalente al de las capturas anteriores — un archivo subido por el endpoint `POST /files/{filename}` aparece en el mismo `pvc-filestore` y es visible desde los pods Python y Spring Boot directo. La única diferencia visible para el cliente es que el body del `POST`/`PUT` debe enviarse como `binary` en vez de `form-data` (el LB de OCI altera el `Content-Type` del multipart, ver [Por qué NO usamos Multipart](#-por-qué-no-usamos-multipart)). Internamente el archivo viaja Spring Boot → SFTP → File Storage, pero el resultado neto sobre el NFS es idéntico al de las otras dos implementaciones.

---

<div align="center">

**Construido con** ☁️ Oracle Cloud Infrastructure · 🐍 Python / FastAPI · ☕ Java / Spring Boot · 🔐 JSch (mwiede) · 📦 atmoz/sftp · ☸️ Kubernetes

</div>