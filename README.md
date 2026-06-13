# GeoSnap Cam

GeoSnap Cam es una aplicación de cámara avanzada para Android que superpone metadatos geoespaciales en tiempo real (coordenadas GPS, fragmentos de mapas, marcas de tiempo, velocidad y direcciones) directamente en capturas de fotos y videos de alto rendimiento.

---

## 🚀 Características Clave

* 🔵 **Codificación de Video en Tiempo Real**: Utiliza `MediaCodec` de Android y un puente JNI en C++ personalizado para procesar fotogramas de video de forma nativa en tiempo real, superponiendo metadatos eficientemente con latencia ultra baja.
* ⚙️ **Adaptación Dinámica de Hardware**: Detecta automáticamente las capacidades del hardware (por ejemplo, formatos de color Planar vs. Semi-Planar) y ajusta la disposición de la memoria para evitar la distorsión del color en todos los dispositivos (incluyendo emuladores x86_64).
* 🎙️ **Captura de Audio Robusta**: Cuenta con sistemas de seguridad inteligentes para audio que sincronizan las marcas de tiempo del video y evitan fallos del Muxer, incluso si el micrófono del dispositivo no responde o presenta retrasos.
* 🗺️ **Obtención de Mapas en Segundo Plano**: Obtiene datos de ubicación mediante Play Services y almacena en caché imágenes de mapas utilizando la Google Static Maps API para un rendimiento fluido de la interfaz de usuario y las superposiciones.
* 🎨 **Interfaz de Usuario Moderna**: Construida completamente en Jetpack Compose siguiendo las directrices de Material 3.

---

## 🏗️ Arquitectura

* 📸 **CameraX API**: Gestiona la interacción con el hardware de la cámara, el análisis de imágenes y las sesiones de captura.
* 🛠️ **Procesamiento Nativo JNI / C++**: Intercepta los fotogramas de `ImageProxy` directamente desde la cámara en formato `YUV_420_888`, superpone la marca de agua y pasa los búferes modificados directamente a `MediaCodec` para una codificación de copia cero (*zero-copy*).
* 📱 **Jetpack Compose**: Gestiona la interfaz de usuario moderna y responsiva del visor de la cámara.
* 🔄 **Corrutinas y Flow**: Utilizados extensamente para la gestión de estados y el sondeo (*polling*) de la ubicación en segundo Plano.

---

## 🔒 Seguridad y Claves

Para compilar este proyecto, deberás proporcionar tus propias claves de API.
👉 **Nota importante:** Estos archivos son estrictamente ignorados por Git para prevenir la filtración de secretos.

1️⃣ **`local.properties`**: Crea este archivo en la raíz del proyecto y añade tu clave de Google Maps API (utilizada para la superposición del mapa estático):

```properties
MAPS_API_KEY=TU_GOOGLE_MAPS_API_KEY_AQUÍ
```

2️⃣ **`key.properties`**: (Opcional para Debug) Utilizado para la firma de la versión de producción (*release*).

```properties
keyAlias=tu_alias
keyPassword=tu_contraseña_de_clave
storeFile=tu_keystore.jks
storePassword=tu_contraseña_de_almacenamiento
```

3️⃣ **`google-services.json`**: Coloca tu archivo de configuración de Firebase dentro de la carpeta `app/`.

---

## ⚠️ Requisitos de Compilación

* 🛠️ Android Studio Ladybug o posterior
* 📦 Android NDK (configurado automáticamente por Gradle)
* 📐 CMake 3.22.1
* ☕ JDK 17
