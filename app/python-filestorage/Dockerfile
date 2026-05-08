FROM python:3.12-slim

WORKDIR /app

# Instalar dependencias del sistema para NFS client (por si acaso)
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app/ ./app/

# Crear directorio de montaje
RUN mkdir -p /mnt/filestore

ENV STORAGE_PATH=/mnt/filestore

EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]