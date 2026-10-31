# AURA Legacy — AI Unified Remote Agent

> ChatGPT'nin Android üzerindeki güvenli cihaz ajanı.

**AURA** ana marka/ürün adı olarak şimdilik ayrılmıştır. Bu depodaki aktif bağımsızlaştırma çalışmasının adı **AURA Legacy**; ilk Ultra Agent tabanlı, APK ile kullanılabilir neslin adı ise **AURA Mix** olarak belirlenmiştir.

## Ürün ve kod tabanı ayrımı

| Ad | Anlamı | Kod / durum |
|---|---|---|
| **AURA** | Ayrılmış ana marka ve gelecekteki ürün kimliği | Şimdilik belirli bir sürüme/kod tabanına bağlanmıyor |
| **AURA Mix** | İlk kullanılabilir nesil; Ultra Agent çatallanması ve mevcut Android ajanı temeli | `main` ve `v1-archive-2026-10-31` tarihsel referansı |
| **AURA Legacy** | Ultra'ya özgü Brain/model/agent çalışma bağımlılıklarını azaltıp kendi güvenli Android ajanı mimarisini kuran yeni çalışma | `develop/aura-legacy` |

AURA Mix'in kaynak kökeni ve üçüncü taraf lisansları korunur. AURA Legacy'nin amacı yalnızca yeniden markalama değildir: kendi runtime, görev akışı, güvenlik/onay, yetenek keşfi, eylem yürütme ve doğrulama sınırlarını oluşturmaktır. Devralınan kod, paket adları veya upstream bileşenler henüz tamamen temizlenmiş/ayrıştırılmış kabul edilmez.

## AURA Legacy mimari yönü

- **ChatGPT tek karar verici ve orkestratör**; Android tarafı Body / Tool / Bridge ve güvenli yürütme katmanıdır.
- ChatGPT bağlantısı OpenAI API üzerinden sağlanacaktır. API anahtarı kaynak koda veya APK'ya gömülmez.
- Ultra Agent'ın yerel Brain/model karar döngüsü ana çalışma yolu olmayacaktır. Uzak görevler, doğrulanmış ChatGPT karar gateway'i ve görev bazlı yetkilendirme tamamlanana kadar kalıcı olarak bekletilir.
- Runtime hafızası cihaz içinde, app-private ve offline kalır; Notion/Cortex/Dropbox runtime bağımlılığı değildir.
- Riskli eylemler görev/eylem bazlı onay, gerçek cihaz yetenek/izin kontrolü ve işlem sonrası doğrulamadan geçer.
- Relay, kalıcı olay/görev koordinasyonu ve güvenli aktarım sağlar; cihaz kendi başına herkese açık uzaktan kontrol soketi açmaz.
- İlk fiziksel test cihazı Samsung Galaxy A54'tür; hedef tasarım tek bir modele kilitlenmez.

## Son doğrulanmış geliştirme noktası

- Repo: `iabilen/AURA-AI-Unified-Remote-Agent`
- Aktif dal: `develop/aura-legacy`
- Son doğrulanmış HEAD (dal yeniden adlandırılmadan önce; SHA değişmedi): `4c1229201144b691f05862ba7a827e78886c375d`
- `main` / AURA Mix tabanı korunmuştur; son bilinen main SHA: `dad5e8137636dc56c6ceb85ad99376465de5bcc1`.
- V2 geliştirme adı artık AURA Legacy olarak değiştirilmiştir. Eski V2 isimleri yalnızca tarihsel commit, dosya yolu veya iç paket adlarında kalabilir; bunlar toplu ve körlemesine yeniden adlandırılmayacaktır.

### Mevcut eklemeler

- Bildirim mahremiyet politikası ve typed ActionPlan sözleşmeleri.
- Şifreli cihaz içi continuity memory, durable event outbox ve inbound task inbox.
- Relay tarafında durable event journal, consumer lease/ACK/retention ve durable task ledger.
- Uzak görevlerin yerel Ultra Brain/model döngüsüne otomatik yönlendirilmesinin kaldırılması.
- Görev/eylem/idempotency anahtarı/süre ile ilişkilendirilen TaskApprovalGate primitive'i.

Bunlar kod tabanında eklenmiş bileşenlerdir; tüm runtime entegrasyonlarının, güvenlik akışının veya gerçek cihaz davranışının tamamlandığı anlamına gelmez.

## Açık işler

1. Kimliği doğrulanmış, şema doğrulamalı ChatGPT karar gateway'i.
2. Replay-safe dispatcher ve cihaz tarafı dedup handshake.
3. TaskApprovalGate'in gerçek onay UI'sı ve executor ile entegrasyonu.
4. Gerçek OS/API/izin durumuna dayalı capability registry.
5. Action adapter'ları ve işlem sonrası doğrulama.
6. Pairing, token rotation/revocation audit, graceful shutdown ve stale-lock recovery.
7. Cihaz bağımsız namespace/config ve OEM/API adaptörleri.
8. Redacted logs/metrics ve hata/çökme senaryolarının tamamlanması.

## Doğrulama ve yayın sınırı

Son kayıt itibarıyla Gradle build, test, lint, entegrasyon testi ve Samsung A54 gerçek cihaz doğrulaması çalıştırılmamıştır. Kod derlenmiş veya üretime hazır kabul edilmez. APK/build, main'e merge, release veya dış dağıtım için İhsan'ın ayrıca açık onayı gerekir.

## Lisans ve köken

AURA Mix'ten devralınan/upstream kodun lisans ve atıf yükümlülükleri korunur. AURA'ya özgü katkılar ile upstream/değiştirilmiş/üçüncü taraf bileşenler ayrı belgelenir. Repo lisansı ve NOTICE dosyaları esas alınır.
