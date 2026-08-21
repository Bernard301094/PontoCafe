# Auditoría de seguridad de identificación facial

## Alcance verificado

- Branch: `agent/direct-authorization-device-ux-20260821`
- Commit base: `11fa0f0494b3b43758ee19bbe67e55e0ad6be8e1`
- Modelo preservado: FaceNet 128D `facenet-128d-160-v1`
- Runtime preservado: LiteRT CPU/XNNPACK, 2 threads
- Lógica preservada: similitud coseno, umbral `0.72` y margen top-1/top-2 `0.06`
- Sin APK, despliegue, migración, PR, merge ni cambios en producción

## Causas de confusión encontradas

1. El Ponto aceptaba una sola captura; los candidatos alternativos eran recortes del mismo frame y no evidencia temporal independiente.
2. La prueba de vida y la captura no quedaban vinculadas de forma suficientemente fuerte al mismo track facial.
3. El frame capturado no volvía a validar de forma centralizada rostro único, tamaño, encuadre, visibilidad, pose y landmarks.
4. La calidad sólo cubría métricas básicas y no rechazaba de forma completa desenfoque, clipping, sombras o contraluz.
5. La confirmación local del servidor y la revalidación offline comparaban principalmente con la identidad reclamada, sin exigir que fuese el mejor candidato global con margen sobre otra persona.
6. Dos fallbacks de recorte podían producir identidades contradictorias sin invalidar necesariamente toda la captura.
7. El alta promediaba cinco muestras sin detectar primero un cambio de persona dentro de la secuencia.
8. La validación del catálogo permitía dimensiones amplias y una sola entrada corrupta podía invalidar el catálogo completo.
9. Había ventanas donde un resultado asíncrono antiguo o un doble toque podían alterar un estado de reconocimiento más nuevo.

## Protecciones implementadas

### Captura, liveness y calidad

- Política pura y testeable para aceptar sólo un rostro válido.
- Rechazo de cero/múltiples rostros, cara demasiado pequeña o grande, cara parcial, mala centralización, pose extrema, landmarks incoherentes y ojos poco visibles.
- La captura queda anclada al track, pose y tiempo de la observación que superó el desafío; expira en 1,5 segundos.
- El liveness conserva sus desafíos actuales, pero ahora mantiene continuidad del mismo track con fallback conservador por IoU.
- Tras completar liveness se exigen frames frontales estables; una captura rechazada reinicia el desafío.
- Métricas de brillo, contraste, nitidez, varianza Laplaciana, clipping oscuro/claro, sombra lateral y contraluz.
- La detección mínima bajó de 0,20 a 0,15 para descubrir antes rostros secundarios; la cara usada para reconocer conserva los límites estrictos de tamaño.

### FaceNet y embeddings

- El recorte canónico, resize 160×160, RGB, prewhitening, tensor, FaceNet y normalización L2 permanecen compatibles con los rostros existentes.
- La alineación por línea de ojos sólo es un fallback posterior a un miss canónico; no cambia el embedding histórico.
- Toda entrada o salida exige exactamente 128 componentes, valores finitos, norma no nula y norma L2 dentro de tolerancia.
- Modelo y versión deben coincidir en app, caché, catálogo y rutas que reciben esos metadatos.

### Matching y ambigüedad

- Cada template se compara por coseno, pero sólo el mejor score de cada colaborador participa en top-1/top-2. Tener más apariencias no da ventaja por cantidad.
- Se acepta únicamente cuando `top1 >= 0.72` y, si existe top-2, `top1 - top2 >= 0.06`.
- La confirmación local y la sincronización offline vuelven a buscar globalmente entre colaboradores activos del mismo modelo/versión.
- Fallbacks contradictorios entre personas invalidan el frame.
- Se mantiene búsqueda exacta. Para el tamaño medido no se justificó ANN/HNSW: añadir aproximación puede omitir el segundo candidato crítico y no mejoraría precisión.

### Consenso temporal y transacciones

- Se requieren dos capturas independientes dentro de 3 segundos.
- Ambas deben producir el mismo colaborador, catálogo, modelo y versión, conservar el track cuando ML Kit lo expone y tener similitud directa mínima de 0,60.
- El embedding medio normalizado se evalúa otra vez contra todo el catálogo antes de mostrar nombre/avatar o llamar al servidor.
- Cualquier desacuerdo reinicia la secuencia; el sistema se niega a adivinar.
- Un epoch descarta resultados viejos después de suspensiones de red/disco.
- Un lease atómico bloquea dobles registros y se libera al finalizar o fallar.

### Alta y catálogo

- El alta nueva envía exactamente cinco muestras guiadas.
- Se elige un medoid, se rechaza cualquier muestra por debajo de 0,60 respecto a la secuencia y sólo después se calcula la media L2.
- El backend verifica las cinco muestras, el consolidado y la continuidad con cualquier apariencia previa de la misma persona.
- La comparación de duplicidad contra otros colaboradores y el advisory lock existentes se conservan y ahora ignoran vectores históricos corruptos.
- El servidor y la app validan ID UUID, nombre, modelo, versión, tipo, dimensión, finitud, norma, IDs de template y duplicados.
- Entradas malas se ponen en cuarentena individual; el resto del catálogo sigue disponible. El conteo rechazado llega a diagnósticos sin exponer imágenes ni vectores.
- El almacenamiento local continúa cifrado con AES-GCM y usa commit síncrono para no publicar en RAM un catálogo que no quedó persistido.

## Diagnósticos

Se exponen sin conservar imágenes, embeddings, tokens ni secretos:

- top-1, top-2 y margen;
- número de candidatos y templates válidos;
- frames rechazados y motivo de calidad;
- avance del consenso;
- latencia total e inferencias;
- modelo y versión de catálogo;
- templates en cuarentena;
- pares de colaboradores con similitud de catálogo anormalmente alta.

## Rendimiento

- Benchmark determinista: 100 colaboradores, 500 templates y 250 búsquedas exactas.
- Últimas ejecuciones: 1,68 ms y 1,75 ms por búsqueda en Node de escritorio; rango observado durante la implementación: 1,63–2,67 ms.
- Vector bruto: 512 bytes por template (128 floats); 500 templates representan aproximadamente 250 KiB, sin contar objetos/metadata.
- Camino exitoso anterior: normalmente una inferencia canónica de un frame.
- Camino exitoso reforzado: normalmente dos inferencias canónicas, una por frame; máximo de tres recortes por frame sólo tras miss.
- La latencia de FaceNet y memoria total en Galaxy real no se pueden medir de forma representativa en este entorno. El diagnóstico local quedó preparado para recoger esos valores en el dispositivo.

## Validación ejecutada

- `:app:testDebugUnitTest --offline --no-daemon`: `BUILD SUCCESSFUL`.
- TypeScript `tsc --noEmit`: correcto.
- Contratos y tests biométricos aislados: 40/40 correctos.
- Suite backend completa: 180/197 correctos. Los 17 fallos restantes son contratos UI/release anteriores y dos tests que buscan `SupervisorLiveScreenV2.kt`, archivo inexistente en el commit base; ninguno pertenece al matching biométrico modificado.
- Benchmark biométrico integrado en los tests.
- No se ejecutó ninguna tarea `assemble`, `bundle`, instalación o generación de APK.

## Dependencias

No se añadió ninguna dependencia. ML Kit, LiteRT, CameraX y Kotlin existentes son suficientes para este catálogo y permiten conservar compatibilidad y tamaño.

`npm audit` detectó una vulnerabilidad moderada preexistente en `hono 4.12.32`, incluido un ReDoS del middleware CORS que el backend utiliza. Existe corrección en Hono `4.12.34+`; debe actualizarse y validarse en un cambio separado: <https://github.com/advisories/GHSA-8j4g-w8fx-2239>.

## Limitaciones y re-enrollment

- Hace falta una prueba física en los modelos Samsung objetivo y un conjunto representativo autorizado para estimar FAR, FRR y top-1 reales; los tests sintéticos no sustituyen validación biométrica formal.
- El liveness actual queda reforzado y coordinado, pero no sustituye un motor PAD certificado frente a ataques sofisticados.
- Los límites de calidad deben observarse en iluminación real antes de afinarlos; no se deben relajar el umbral o margen para reducir rechazos.
- La búsqueda exacta es preferible al tamaño actual. Debe reevaluarse un índice ANN sólo si el catálogo crece lo suficiente y puede demostrarse que conserva de forma segura top-1 y top-2.

No se recomienda re-enrollment global. Sólo corresponde recapturar a una persona cuando su único template queda en cuarentena, usa otro modelo/versión, falla repetidamente la calibración con buena captura, existe una colisión persistente con otra identidad o su apariencia cambió y no queda ninguna plantilla válida compatible.
