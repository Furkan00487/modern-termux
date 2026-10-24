# Release Key ve Secret Yönetimi Uygulama Planı

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eski release secret'larını temizlemek, Termux üzerinde 32 haneli şifreli ve 39 yıl geçerli yeni release anahtarı üretmek, ~/storage/downloads dizinine yedeklemek ve hem Actions hem Codespaces secret'larına kaydetmek.

**Architecture:** Cihazda RAM'de Python secrets ile şifre üretimi -> keytool ile PKCS12 keystore oluşturma -> mv ile indirme dizinine yedekleme -> gh CLI ile Actions & Codespaces secret senkronizasyonu -> apksigner doğrulaması.

**Tech Stack:** Bash, Python 3.14 (secrets), Keytool 25.0.5, GitHub CLI (gh), apksigner.

**Spec:** `docs/superpowers/specs/2026-10-24-release-key-management-design.md`

## Global Constraints
- Şifre üretimi ve geçici işlemler diske dosya yazmadan RAM üzerinde (`python3 -c "..."`) yürütülecektir.
- Keystore formatı PKCS12, anahtar algoritması RSA 4096-bit olacaktır.
- Geçerlilik süresi 39 yıl (14245 gün) olacaktır.
- Anahtar dosyası yedeklenirken `cp` yerine doğrudan `mv` kullanılacaktır.
- Özel anahtar ve şifre kesinlikle git deposuna eklenmeyecektir (.gitignore korunacaktır).

## Review Focus
1. Eski secret'ların silindiğinin doğrulanması.
2. Üretilen şifrenin tam 32 karakter ve kriptografik rastgelelikte olması.
3. Keystore'un 14245 gün geçerli ve PKCS12 formatında olduğunun `keytool -list -v` ile teyit edilmesi.
4. Hem GitHub Actions hem GitHub Codespaces secret listelerinde 3 secret'ın da güncellendiğinin teyidi.
5. `~/storage/downloads/modern-termux-release.p12` dosyasının erişilebilir ve sağlam olduğunun teyidi.

---

### Task 1: Eski Secret'ların Temizlenmesi

**Files:**
- Modify: GitHub Repo Secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEYSTORE_ALIAS`)

**Interfaces:**
- Consumes: `gh secret delete`
- Produces: Temizlenmiş secret havuzu

- [ ] **Step 1: Mevcut Actions secret'larını sil**
```bash
gh secret delete KEYSTORE_BASE64 --repo Furkan00487/modern-termux
gh secret delete KEYSTORE_PASSWORD --repo Furkan00487/modern-termux
gh secret delete KEYSTORE_ALIAS --repo Furkan00487/modern-termux
```

- [ ] **Step 2: Silme işlemini doğrula**
Run: `gh secret list --repo Furkan00487/modern-termux`
Expected: Boş veya ilgili anahtarların listede olmadığını gösteren çıktı.

---

### Task 2: 32 Haneli Şifre Üretimi ve 39 Yıllık Keystore Oluşturulması

**Files:**
- Create (Geçici): `/tmp/modern-termux-release.p12`

**Interfaces:**
- Consumes: Python `secrets` modülü, `keytool 25.0.5`
- Produces: `/tmp/modern-termux-release.p12` ve 32 karakterlik parola

- [ ] **Step 1: RAM'de Python secrets ile 32 karakterli güçlü şifre ve keystore oluştur**
Alias: `termux-release`
Validite: `14245` gün (39 yıl)
Keystore: `/tmp/modern-termux-release.p12`

- [ ] **Step 2: Keystore geçerlilik ve parametrelerini doğrula**
Run: `keytool -list -v -keystore /tmp/modern-termux-release.p12 -storetype PKCS12 -storepass "$PASS"`
Expected: Alias `termux-release`, Valid from ... until 2065 (39 yıl), RSA 4096-bit.

---

### Task 3: Keystore Dosyasının ~/storage/downloads Dizinine Yedeklenmesi

**Files:**
- Move: `/tmp/modern-termux-release.p12` -> `~/storage/downloads/modern-termux-release.p12`

**Interfaces:**
- Consumes: `mv`
- Produces: `~/storage/downloads/modern-termux-release.p12`

- [ ] **Step 1: Dosyayı mv ile Download dizinine taşı**
```bash
mv /tmp/modern-termux-release.p12 ~/storage/downloads/modern-termux-release.p12
```

- [ ] **Step 2: Yedek dosyasını doğrula**
Run: `ls -lh ~/storage/downloads/modern-termux-release.p12`
Expected: Dosya mevcut ve okunabilir.

---

### Task 4: Secret'ların GitHub Actions ve Codespaces'e Yüklenmesi

**Files:**
- Modify: GitHub Actions Secrets & Codespaces Secrets

**Interfaces:**
- Consumes: `base64`, `gh secret set`
- Produces: Yüklenmiş `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEYSTORE_ALIAS`

- [ ] **Step 1: Actions secret'larını ayarla**
```bash
gh secret set KEYSTORE_BASE64 -b "$KEYSTORE_B64" --repo Furkan00487/modern-termux
gh secret set KEYSTORE_PASSWORD -b "$PASS" --repo Furkan00487/modern-termux
gh secret set KEYSTORE_ALIAS -b "termux-release" --repo Furkan00487/modern-termux
```

- [ ] **Step 2: Codespaces secret'larını ayarla**
```bash
gh secret set KEYSTORE_BASE64 -b "$KEYSTORE_B64" --app codespaces --repo Furkan00487/modern-termux
gh secret set KEYSTORE_PASSWORD -b "$PASS" --app codespaces --repo Furkan00487/modern-termux
gh secret set KEYSTORE_ALIAS -b "termux-release" --app codespaces --repo Furkan00487/modern-termux
```

- [ ] **Step 3: Her iki ortamda secret'ların tanımlandığını doğrula**
Run:
`gh secret list --repo Furkan00487/modern-termux`
`gh secret list --app codespaces --repo Furkan00487/modern-termux`
Expected: Her iki komut çıktısında da 3 anahtarın listelenmesi.

---

### Task 5: Doğrulama ve Raporlama

- [ ] **Step 1: Keystore yedeği ve GitHub secret durumunu doğrula**
- [ ] **Step 2: Kullanıcıya şifre, geçerlilik süresi ve yedek dosya konumunu raporla**
