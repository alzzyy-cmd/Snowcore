# SnowNW Core · v0.3.0 (Yenilenmiş — 26.1)

EconomySMP Core V2'den ilhamla, **tamamen Türkçe** Paper 26.1 eklentisi. Tüm mesajlar, GUI'ler, komutlar ve takma adlar Türkçedir.

## Bu sürümde yapılanlar

### Hata / mantık düzeltmeleri
- **Renk sistemi:** `&#RRGGBB` hex kodları artık doğru çözülüyor (`ColorUtil` hex + x-format desteği). Bütün menüler/skorbord modern palet uyumlu.
- **Ev yuvaları:** `homes.default-slots` (varsayılan 2) + `snownwcore.home.<n>` yetkileri sunucu limitiyle sınırlı (`homes.max-homes`).
- **Ender Sandığı:** İki oyuncunun aynı sandığı aynı anda açmasıyla oluşan **kopyalama (dupe)** kilitlendi. Kaydedici hatası giderildi.
- **Takım sistemi:** Üye sınırı config'den (`team.max-members`), isim uzunluk sınırları + yasaklı adlar, `/takim sohbet` (aç/kapa + direkt mesaj), `/takim ff` dost ateşi, kayıtta kaybolmalar giderildi.
- **Klasik menüler:** Bildirimler/Görünüm/Skor tablosu sayfalarının boş görünmesi (kategori anahtar uyumsuzluğu) düzeltildi; Ekonomi kategorisi eklendi; tüm metinler Türkçeleştirildi; `home-set` `{name}` değişkeni dolduruluyor.
- **Dondurma (freeze):** `setFreezeTicks`'in kar hasarıyla oyuncuyu **öldürmesi** giderildi — artık potion bazlı; kamera açısı korunuyor; `snownwcore.admin.bypassfreeze` eklendi.
- **Ses:** MobHide/PvP ayarlarındaki `stopAllSounds()` çağrısı tüm sesleri kesiyordu; kaldırıldı.
- **Spawner:** `spawners.require-silk-touch` — İpeksi Dokunuş şartı.
- **TPA:** `[KABUL ET]` tıklama metni Türkçeleştirildi; oynama süresi formatı `1g 2sa 3dk`.
- **Diyaloglar:** "Takım sistemi kaldırıldı" hatası düzeltildi; Shard sıralaması gerçek shard verisini gösteriyor; ödeme tutarları (100/1K/10K/100K/1M/10M + özel) düzeltildi; `minimum-protocol: 772` klasik menüye düşüş doğru çalışıyor.
- **Shard mağazası/Pazar:** HasItems↔RemoveItems aynı kaynak havuzunu kullanıyor (kaybolan eşya sorunu giderildi).
- `menu` komut takma adı çakışması (`snownwcore`) giderildi.

### Eklenen yeni sistemler (V2 paritesi)
| Sistem | Komut (Türkçe takma adlar) |
|---|---|
| Satış | `/sat` (eldeki), `/sathepsi` (tüm envanter) — `snownwcore.sellmultiplier.<n>` yetki çarpanı |
| Sunucu Marketi | `/market` (`/magaza`, `/mağaza`) — kategorili sandık GUI |
| Sipariş (alım emri) | `/siparis` + diyalog — escrow, kısmi dolum, iptalde iade |
| Kafa Ödülü | `/odul <oyuncu> <miktar>`, `/odul liste`, diyalog GUI |
| Ölüm Mesajları | `death-messages.yml` — nedene göre rastgele Türkçe mesajlar |
| ClearLag | `/lagtemizle` + otomatik periyodik temizlik + uyarı geri sayımı |
| Key-All | `/keyall`, `key-all.*` otomatik periyod |
| Çift Zıplama | `/çiftzıpla` (toggle), düşme hasarı koruması |
| Shard Pazarı | `/shardmarket`, `shard-shop.yml` GUI |
| Sohbet Formatı | V2 tarzı format + hover'da istatistik kartı (LuckPerms prefix desteği) |
| Öldürme Shards | `shards.per-kill` + cooldown + actionbar bildirimi |
| Hasar Ayarları | End kristali / diriliş çapası hasar çarpanları (`end-crystal`, `respawn-anchor`) |
| Yeniden Doğuş Kiti | `respawn-kit` (varsayılan zincir zırh) |
| Otomatik AFK | Spawn alanında `afk-system.idle-seconds` hareketsiz kalınca AFK alanına taşıma |
| Ek komut kayıtları | `/cuboid` (`/kup`), `/baltop` (`/zenginler`), `/shardmanager` (`/shardyonetim`) |

### Yapılandırma
- `config.yml` bölümleri: `sell`, `orders`, `bounty`, `clearlag`, `key-all`, `double-jump`, `end-crystal`, `respawn-anchor`, `respawn-kit`, `afk-system`, `chat-format`, `teleport-delays` (tür bazlı ışınlanma beklemeleri), `homes.default-slots`, `team.*` (isim kuralları), `spawners.require-silk-touch`, `shards.per-kill/cells-on-kill`.
- `messages.yml` tamamen Türkçe, tutarlı palete gözden geçirildi (`&b` birincil, `&a` başarı, `&c` hata, `&d` shard, `&e` uyarı).
- `offenses.yml` ceza adları Türkçe.

## Derleme
GitHub Actions (`.github/workflows/build.yml`) her push'da derler; jar `build/` klasörüne düşer.
Elle: `mvn -B package -Dpaper.version=<26.1.x-build.N-stable>` (JDK 21+ önerilir; 26.1.2 API'si ile derlenir).

## Kurulum
1. `SnowNWCore-0.3.0-26.1.jar` → `plugins/`
2. Paper 26.1+ sunucuyu başlat (Java 21+).
3. Opsiyonel: PlaceholderAPI, Vault (+ ekonomi eklentisi), LuckPerms.
