from fastapi import FastAPI, UploadFile, File, HTTPException
from fastapi.responses import FileResponse, JSONResponse
import aiofiles
import os
import shutil
from pathlib import Path
from datetime import datetime

app = FastAPI(
    title="OCI FileStorage CRUD API",
    description="Microservicio CRUD sobre OCI File Storage montado como NFS",
    version="1.0.0"
)

# Este path es donde se monta el PVC dentro del pod
STORAGE_PATH = Path(os.getenv("STORAGE_PATH", "/mnt/filestore"))
STORAGE_PATH.mkdir(parents=True, exist_ok=True)


# ──────────────────────────────────────────
# HEALTH CHECK
# ──────────────────────────────────────────
@app.get("/health")
def health_check():
    return {"status": "ok", "storage_path": str(STORAGE_PATH)}


# ──────────────────────────────────────────
# CREATE — Subir archivo
# ──────────────────────────────────────────
@app.post("/files/{filename}", status_code=201)
async def upload_file(filename: str, file: UploadFile = File(...)):
    dest = STORAGE_PATH / filename

    if dest.exists():
        raise HTTPException(
            status_code=409,
            detail=f"El archivo '{filename}' ya existe. Usa PUT para actualizarlo."
        )

    try:
        async with aiofiles.open(dest, "wb") as out:
            content = await file.read()
            await out.write(content)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Error guardando archivo: {str(e)}")

    return {
        "message": "Archivo subido exitosamente",
        "filename": filename,
        "size_bytes": dest.stat().st_size,
        "created_at": datetime.utcnow().isoformat()
    }


# ──────────────────────────────────────────
# READ — Descargar archivo
# ──────────────────────────────────────────
@app.get("/files/{filename}")
async def download_file(filename: str):
    dest = STORAGE_PATH / filename

    if not dest.exists():
        raise HTTPException(status_code=404, detail=f"Archivo '{filename}' no encontrado")

    return FileResponse(
        path=str(dest),
        filename=filename,
        media_type="application/octet-stream"
    )


# ──────────────────────────────────────────
# READ ALL — Listar archivos
# ──────────────────────────────────────────
@app.get("/files")
async def list_files():
    try:
        files = []
        for f in STORAGE_PATH.iterdir():
            if f.is_file():
                stat = f.stat()
                files.append({
                    "filename": f.name,
                    "size_bytes": stat.st_size,
                    "modified_at": datetime.utcfromtimestamp(stat.st_mtime).isoformat()
                })
        return {"files": files, "total": len(files)}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


# ──────────────────────────────────────────
# UPDATE — Reemplazar archivo existente
# ──────────────────────────────────────────
@app.put("/files/{filename}")
async def update_file(filename: str, file: UploadFile = File(...)):
    dest = STORAGE_PATH / filename

    if not dest.exists():
        raise HTTPException(
            status_code=404,
            detail=f"Archivo '{filename}' no existe. Usa POST para crearlo."
        )

    try:
        async with aiofiles.open(dest, "wb") as out:
            content = await file.read()
            await out.write(content)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Error actualizando archivo: {str(e)}")

    return {
        "message": "Archivo actualizado exitosamente",
        "filename": filename,
        "size_bytes": dest.stat().st_size,
        "updated_at": datetime.utcnow().isoformat()
    }


# ──────────────────────────────────────────
# DELETE — Eliminar archivo
# ──────────────────────────────────────────
@app.delete("/files/{filename}")
async def delete_file(filename: str):
    dest = STORAGE_PATH / filename

    if not dest.exists():
        raise HTTPException(status_code=404, detail=f"Archivo '{filename}' no encontrado")

    try:
        dest.unlink()
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Error eliminando archivo: {str(e)}")

    return {"message": f"Archivo '{filename}' eliminado exitosamente"}


# ──────────────────────────────────────────
# INFO — Metadata de un archivo
# ──────────────────────────────────────────
@app.get("/files/{filename}/info")
async def file_info(filename: str):
    dest = STORAGE_PATH / filename

    if not dest.exists():
        raise HTTPException(status_code=404, detail=f"Archivo '{filename}' no encontrado")

    stat = dest.stat()
    return {
        "filename": filename,
        "size_bytes": stat.st_size,
        "created_at": datetime.utcfromtimestamp(stat.st_ctime).isoformat(),
        "modified_at": datetime.utcfromtimestamp(stat.st_mtime).isoformat(),
        "path": str(dest)
    }