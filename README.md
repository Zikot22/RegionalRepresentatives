1. Настройка сервера
   
cd RegionalRepresentativesServer
call .venv\Scripts\activate.bat
python -m pip install fastapi "uvicorn[standard]"
python -m uvicorn main:app --reload

2. Запускаем Android Studio

Собираем проект, скачиваем и собираем виртуалку, 
пишем в терминал ```& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" reverse tcp:8000 tcp:8000```
и запускаем приложение
