@echo off
title TouchDeck E2E Test Suite
cd /d "%~dp0tests"
python -m pip install websockets -q
python test_companion_client.py 127.0.0.1 9876
pause
