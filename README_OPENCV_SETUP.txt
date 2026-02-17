OpenCV (ВАЖНО)

В этой версии проекта OpenCV подключается автоматически через Maven Central как зависимость Gradle:
  implementation("org.opencv:opencv:4.9.0")

Это официальный вариант, который поддерживается с OpenCV 4.9.0 и не требует импортировать SDK-модуль вручную. 
См. официальную документацию OpenCV: "Android Development with OpenCV" (раздел про Maven Central).

Что делать тебе:
1) Просто открой проект в Android Studio и сделай Gradle Sync.
2) Никаких папок opencv/ и Import Module больше не нужно.
3) Если хочешь обновить версию OpenCV — поменяй версию в app/build.gradle.kts.
