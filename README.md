<p align="center">
  <img src="assets/demo.png" width="340" />
</p>

<h1 align="center">CleanFrame</h1>
<p align="center">
  <b>Android (Kotlin + Jetpack Compose) приложение для удаления водяных знаков с помощью Inpainting (OpenCV).</b>
</p>

<p align="center">
  <a href="#-features">Features</a> •
  <a href="#-tech-stack">Tech Stack</a> •
  <a href="#-how-it-works">How it works</a> •
  <a href="#-setup--run">Setup</a> •
  <a href="#-build-apk">Build APK</a> •
  <a href="#-project-structure">Structure</a>
</p>

---

## 🔥 Features

- 🖼️ **Pick Image** — выбор изображения из галереи
- ✍️ **Mask drawing** — рисование маски поверх водяного знака
- 🎯 **Pixel-perfect mapping** — точное сопоставление координат экрана и Bitmap (1:1)
- 🧠 **OpenCV Inpainting (Telea)** — восстановление области по маске
- 🧽 **Clear Mask** — мгновенная очистка маски
- 💾 **Save to Gallery** — сохранение результата в галерею
- ⚡ **Fast touch input** — мгновенный отклик на касание (без “slop” и задержек)

---

## 🧰 Tech Stack

- **Kotlin**
- **Jetpack Compose (Material3)**
- **MVVM** (ViewModel + state)
- **Coroutines** (`Dispatchers.IO` для CV-обработки)
- **Coil** (`coil-compose`) для загрузки изображений
- **OpenCV Android SDK module** (`:opencv`) — Inpainting

---

## 🧠 How it works

### 1) Пользователь рисует маску
- На экране рисуется полупрозрачная красная маска.
- Каждая точка касания **переводится в координаты Bitmap** с учётом масштаба и смещения.

### 2) Генерируется mask Mat под OpenCV
- Создаётся `CV_8UC1` матрица (чёрная).
- Рисуем на ней белым цветом (255) по пользовательской маске.

### 3) Inpainting
Используем Telea-алгоритм:

```text
Photo.inpaint(src, mask, dst, radius = 3.0, method = INPAINT_TELEA)
<img width="395" height="958" alt="Снимок экрана (756)" src="https://github.com/user-attachments/assets/d4910b05-a162-48c1-97f4-4f345f16f45d" />
