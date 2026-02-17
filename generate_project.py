import os, textwrap

PROJECT_NAME = "CleanFrame"

def write_file(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(textwrap.dedent(content).lstrip("\n"))

# Этот файл оставлен как "справка".
# В ZIP уже лежит готовая структура проекта. Просто распакуй и открой в Android Studio.
print("Проект уже сгенерирован в ZIP. Распакуй CleanFrame.zip и открой папку CleanFrame в Android Studio.")
