CRESTS DE EQUIPOS DE EVENTOS (Argentina / Brasil)
==================================================

Estas crests se muestran debajo del nombre de cada jugador durante los
eventos, igual que una crest de clan.

Archivos:
  Crest_90001.dds  -> crest del equipo Argentina (celeste)
  Crest_90002.dds  -> crest del equipo Brasil (verde)

REEMPLAZAR POR LA BANDERA REAL
------------------------------
Los archivos actuales son placeholders de color solido. Para poner las
banderas reales hay que convertir la imagen a:

  - Formato DDS comprimido DXT1
  - Tamano 16x16 pixeles
  - Tamano de archivo EXACTAMENTE 256 bytes

IMPORTANTE: el servidor (CrestCache) valida el tamano y si el archivo no
tiene exactamente 256 bytes LO BORRA al iniciar. Verifica el tamano antes
de copiar.

Como convertir (con GIMP):
  1. Imagen -> Escalar imagen a 16x16.
  2. Archivo -> Exportar como... -> nombre.dds
  3. En "Comprimir" elegir DXT1 (BC1) y desmarcar mipmaps.
  4. El archivo debe pesar 256 bytes. Si pesa distinto, no sirve.

Estos archivos se copian al servidor con compile.bat (carpeta
data/crests del datapack) y el servidor los lee de ./data/crests/.
