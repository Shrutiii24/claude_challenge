## SMART DATA ACTIONABLE ENGINE

# What this project is

This repo is a mobile (Android, Kotlin + Jetpack Compose) voice-first AI assistant that turns natural language into actionable device commands (system toggles, screenshots/screen recordings, calls/messages, alarms/reminders, app control) and also runs on-device LLMs for chat/automation using the RunAnywhere SDK. It's built for hands-free interaction and privacy (on-device LLMs). 

# Quick highlights (what you get)

- True hands-free wake-word (“Hey Jarvis”) detection and automatic voice input. 
- Voice → voice conversations (app speaks responses when you used voice input). 
- Device/system control (WiFi, Bluetooth, DND, flashlight, mobile data, airplane mode — with platform fallbacks where the OS restricts programmatic toggles). 
- Screenshot and screen recording via voice/text commands (files saved to gallery; MP4/H264 for recordings, PNG for screenshots). 
-On-device LLM support via RunAnywhere SDK: download/manage models, streaming responses, model loading/unloading

# Supported features & commands

-Use these exact or very similar phrasings — the parser supports many variations / fuzzy matching, but these are canonical examples.
-Wake word & voice flow
-Enable: tap mic → say “Hey Jarvis” → app says “Hey, I’m listening” → give command. 


System settings (voice) :
-WiFi: turn on wifi / turn off wifi
-Bluetooth: turn on bluetooth / turn off bluetooth
-DND: enable dnd / disable dnd
-Airplane mode / Mobile data: opens settings panel on newer Android builds (due to OS restrictions). 

Calls, SMS, WhatsApp, Email: 
-Calls: call John or call 1234567890
-SMS: text Mom saying I'll be late
-WhatsApp message: whatsapp John saying hello
-WhatsApp call: whatsapp call Alice or whatsapp video call Bob
-Email generation (AI): generate email to boss@company.com about meeting context project update (app drafts the email and opens Gmail). 

Media & apps: 
-play [song name] or play [song] by [artist] (Spotify / YouTube Music via intelligent fallback).
-search youtube for [query]
-open [app name] or launch [app]. 

Productivity / reminders: 
-Alarms: set alarm for 7am or wake me up at 6:30am
-Timers: set timer for 10 minutes
-Reminders: remind me to buy milk at 5pm tomorrow
-To-do lists: create a to-do list for groceries and add apples, bananas → show my groceries list. 

# Input formats:
The app supports natural language — but the following formats are the most reliable:
Simple verb-first commands (system, media, screenshots):
verb + object → e.g. turn on wifi, open spotify. 

Contact actions:
call <contact name> or call <phone number>
text <contact name> saying <message>

Messages / Email generation:
whatsapp <contact> saying <message>
generate email to <email> about <subject> context <details> → app uses AI to draft body. 

To-dos & reminders:
create a to-do list for <title> and add <item1, item2, ...>
remind me to <task> at <time/date> — supports today, tomorrow, 7am, 6:30pm, or explicit dates like June 1, 2025. 


* Initialize SDK in your Application class, register service providers (LlamaCpp provider), register models (via URL or local AAR/JitPack), then downloadModel() and loadModel() before inference. Streaming token responses are supported for real-time UI. See the SDK guide for exact API usage and recommended models. 

* Wake-word detection restarts automatically after a voice interaction (fix for single-trigger behavior). 
 Grant MediaProjection when asked (screenshot/record). 
