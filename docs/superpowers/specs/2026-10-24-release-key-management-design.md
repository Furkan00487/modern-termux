# Release Key ve Secret Yönetimi Tasarım Dokümanı (Design Doc)

**Tarih:** 2026-10-24  
**Yazar:** Antigravity & Furkan00487  
**Durum:** Onaylandı

## 1. Amaç ve Kapsam (Intent & Why)
Eski release anahtarlarını ve GitHub üzerindeki eski secret'ları tamamen temizleyerek; Termux ortamında yerel olarak 39 yıl geçerli, yüksek güvenlikli 4096-bit RSA PKCS12 formatında yeni bir Android Release Keystore üretmek. Üretilen anahtar bilgilerini hem GitHub Actions hem de GitHub Codespaces secret havuzlarına tanımlamak ve anahtar dosyasını yedekleme amacıyla Android İndirilenler (`~/storage/downloads`) dizinine taşımak.

## 2. Hedefler & Anti-Hedefler
- **Hedefler (Goals):**
  - Eski GitHub Actions secret'larını (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEYSTORE_ALIAS`) silmek.
  - 32 karakterlik kriptografik olarak güvenli şifreyi RAM'de Python `secrets` modülü ile üretmek.
  - Termux yerel ortamında `keytool 25.0.5` kullanarak 39 yıl (14245 gün) geçerli `release.p12` oluşturmak.
  - Anahtar dosyasını `mv` kuralına uygun şekilde `~/storage/downloads/modern-termux-release.p12` konumuna taşımak.
  - Yeni secret'ları (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEYSTORE_ALIAS`) GitHub Actions ve GitHub Codespaces için ayarlamak.
  - Release APK imzalama sürecinin yeni anahtarla sorunsuz çalıştığını doğrulamak.
- **Anti-Hedefler (Non-Goals / Anti-Goals):**
  - Anahtar şifresini veya özel anahtarı git reposuna commitlememek (asla repoya girmemeli).
  - Şifre üretimi için geçici `.py` dosyası oluşturmamak (RAM kuralı).

## 3. Kısıtlamalar (Constraints)
- Cihaz: Termux (Android).
- Keystore Formatı: PKCS12 (`release.p12`).
- Şifre Uzunluğu: 32 karakter (`python3 -c "import secrets, string; ..."`).
- Geçerlilik Süresi: 39 yıl (14245 gün).
- Yedekleme Hedefi: `~/storage/downloads/modern-termux-release.p12`.
- Secret Hedefleri:
  - Repository Actions Secrets
  - Repository Codespaces Secrets

## 4. Yaklaşımlar (Approaches)
- **Yaklaşım 1:** Bulutta (Codespace/Actions) anahtar üretimi. (Elenmiştir; kullanıcı anahtarın kendi cihazında üretilmesini ve yerel yedek alınmasını istedi).
- **Yaklaşım 2 (Seçilen):** Yerel cihazda (Termux) Python RAM ve `keytool` ile üretim, `~/storage/downloads` dizinine taşıma ve `gh` CLI ile hem Actions hem Codespaces secret'larına çift yönlü yükleme.
