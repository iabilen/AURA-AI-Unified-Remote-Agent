# AURA — AI Unified Remote Agent

> **ChatGPT'nin Android üzerindeki fiziksel ajanı.**

AURA (AI Unified Remote Agent), doğrulanmış Ultra Agent Android temelinin üzerine kurulan tek-Brain mimarisidir. **Brain merkezi orkestratör olarak kalır; ikinci bir AI veya yeni bir agent framework eklenmez.**

## Mimari

```text
User / Device Event
        ↓
      Brain
        ↓
      Tools
        ↓
   ActionGate
        ↓
     Android
        ↓
   Verification
        ↓
 Event Queue / ACK
```

Brain; mevcut araç döngüsünü, belleği, tarifleri, scam kontrollerini, güvenlik kapısını ve doğrulama akışını korur. AURA runtime içinde Gemma, FunctionGemma veya llama.cpp tabanlı yerel model bulunmaz.

## AI sağlayıcısı

AURA, mevcut provider sınırı üzerinden **OpenAI Responses API** kullanır. Android istemcisi yalnızca mevcut tur için gerekli sınırlı konuşma bağlamını gönderir ve `store=false` kullanır; böylece AURA'nın çalışma zamanı sürekliliği cihaz-yerel kalır.

Model kimliği yapılandırılabilir; mevcut varsayılan `gpt-6-luna`'dır. API anahtarı çalışma zamanında kullanıcı tarafından sağlanır; kaynak koda veya APK içine gömülmez. Sağlayıcı hatası başka bir AI'a otomatik geçiş yapmaz ve ActionGate/doğrulama katmanlarını bypass etmez.

## AURA'nın koruduğu yetenekler

- İzin verilen Android araçlarını mevcut accessibility/device katmanı üzerinden çalıştırma.
- Deterministik güvenlik kapısı.
- İşlem sonrası doğrulama.
- Event Queue / ACK semantiği.
- Cihaz-yerel çalışma zamanı sürekliliği.
- Mevcut chat ve voice yüzeyleri.
- Başarılı görev dizileri ve rutinleri mevcut memory sistemi üzerinden hatırlama.

## Android 16 durumu

- `compileSdk = 36`
- `targetSdk = 36`
- `minSdk = 28`
- JDK 17
- AGP 8.10.1

Android 15+ 16 KB sayfa boyutu gereksinimi için AURA'nın kendi native LLM/CMake katmanı kaldırılmıştır; mevcut uygulama build'inde artık yerel model native runtime'ı bulunmaz.

## Doğrulama sınırları

- Gerçek kullanıcı OpenAI credential'ı ile canlı API çağrısı henüz çalıştırılmadı.
- Tam A54 fiziksel uçtan uca doğrulama henüz yapılmadı.
- APK üretimi ayrı ve açıkça istenen son adımdır; normal doğrulama CI'ı APK yayınlamaz/üretmez.

## Geliştirme kuralı

**AURA'yı gereksiz yere büyütme.** Mevcut `Brain → Tools → ActionGate → Android → Verification → Event Queue/ACK` zinciri korunur. Yeni AI, yeni agent katmanı veya gereksiz yeniden yazım ancak mevcut tasarım yetersizliği kanıtlanırsa gündeme gelir.

APK, kullanıcı açıkça istemeden oluşturulmaz. Hardening çalışmaları onaylanan kapılar geçmeden `main` dalına birleştirilmez.

## Lisans

AGPL-3.0. Kaynak proje ve atıf bilgileri `LICENSE` ve `NOTICE` dosyalarında korunur.
