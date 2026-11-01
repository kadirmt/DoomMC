# DoomMC

**Minecraft içinde dev bir ekranda Doom tarzı bir nişancı oyunu oyna.**

[English](README.md) · [İndir](https://github.com/kadirmt/DoomMC/releases) · [Sorun bildir](https://github.com/kadirmt/DoomMC/issues)

DoomMC, Minecraft'a tek eşya ile kurulan **25 × 16 blokluk dev bir ekran** ve bu ekrana eşleştirilen bir **Doomplayer Kumandası** ekler. Ekranın karşısına geçip oyunu kontrol edebilir, yakındaki diğer oyuncuların aynı oyunu izlemesini sağlayabilirsin.

Oyun, **320 × 200** çözünürlükte görüntü üreten ve **35 Hz** simülasyon çalıştıran bağımsız bir Java raycasting motoru kullanır. Dokular ve karakter görselleri kodla üretilir; harici DOOM kurulumu veya WAD dosyası gerekmez.

## Özellikler

- Tek ekran eşyası ile otomatik kurulan, oyuncuya dönük 400 blokluk ekran.
- Ekranla eşleştirilen kumanda ve sağ tıklayarak kontrolü devralma.
- Fareyle nişan alma, klavye kontrolleri, üç silah, sağlık, zırh ve cephane toplama.
- Üç hazır bölüm: **Hangar**, **Refinery** ve **Inferno**.
- Dört düşman türü: zombi, imp, demon ve baron.
- Sunucuda çalışan oyun: her ekranı bir kişi kontrol eder, yakındaki oyuncular izler.
- Kontrol bırakıldığında duraklama ve başka bir oyuncunun oyuna devam edebilmesi.
- Oyun içi göstergeler dahil Türkçe, İngilizce, Almanca ve İspanyolca desteği.
- Minecraft 1.20.1 ve 1.21.1 için ayrı Fabric ve Forge sürümleri.

## Uyumluluk

| Minecraft | Mod yükleyici | Oynamak için Java | Derlemede kullanılan yükleyici/API |
| --- | --- | --- | --- |
| 1.20.1 | Fabric | 17+ | Fabric Loader 0.16.10, Fabric API 0.92.2+1.20.1 |
| 1.20.1 | Forge | 17+ | Forge 47.4.10 |
| 1.21.1 | Fabric | 21+ | Fabric Loader 0.16.10, Fabric API 0.116.2+1.21.1 |
| 1.21.1 | Forge | 21+ | Forge 52.1.14 |

Hem Minecraft sürümüne hem de mod yükleyicine uygun JAR dosyasını kullan. Çok oyunculu kullanımda sunucuya ve katılan her istemciye aynı mod sürümünü kur. Fabric sürümleri ayrıca Fabric API gerektirir.

## Kurulum

1. Minecraft sürümüne uygun Fabric veya Forge yükleyicisini kur.
2. [Releases](https://github.com/kadirmt/DoomMC/releases) sayfasından uygun `doommc-<yükleyici>-<minecraft>-1.0.0.jar` dosyasını indir veya kaynak koddan derle.
3. Modun JAR dosyasını Minecraft profilinin `mods` klasörüne kopyala.
4. Fabric kullanıyorsan uygun Fabric API dosyasını ayrıca aynı klasöre koy.
5. Minecraft'ı aç. Ekran ve kumandayı **DoomMC** yaratıcı mod sekmesinde bulabilir veya hayatta kalma modunda üretebilirsin.

## Nasıl oynanır?

1. Ekran için **25 blok genişliğinde, 16 blok yüksekliğinde** boş bir alan hazırla.
2. **Dev Ekran** eşyasıyla bir yüzeye sağ tıkla. Tıkladığın konum ekranın alt sırasının ortası olur; ekran sana doğru bakar.
3. **Doomplayer Kumandası** ile ekranın herhangi bir parçasına sağ tıklayıp eşleştir.
4. Ekranı görebildiğin bir noktaya geç ve eşleştirilmiş kumandayla havaya sağ tıklayarak kontrolü devral.
5. Oyuna başlamak için **Enter** tuşuna bas veya ateş et.
6. **F10** ile kontrolü bırakıp nişancı oyununu duraklat. Eşleştirilmiş kumandayla eğilerek sağ tıklamak oturumu kapatır.

Sen oynarken Minecraft dünyası çalışmaya devam eder; bu, tek oyunculu dünyalar için de geçerlidir. Duraklatılan oyun bellekte tutulur, 30 dakika boyunca kimse kontrolü almazsa silinir. Sunucu kapanınca oturumlar da silinir.

### Kontroller

| Eylem | Tuş |
| --- | --- |
| İleri / geri | W / S veya Yukarı / Aşağı ok |
| Sola / sağa yan adım | A / D veya Q / E |
| Dönme | Fare veya Sol / Sağ ok |
| Ateş etme | Sol fare tuşu veya Ctrl |
| Kapı açma | Boşluk |
| Koşma | Shift |
| Tabanca / pompalı / makineli seçme | Silahı aldıktan sonra 1 / 2 / 3 |
| Başlatma / tekrar deneme / devam etme | Enter veya ateş |
| Kontrolü bırakma ve nişancı oyununu duraklatma | F10 |

### Üretim tarifleri

**Dev Ekran** — 5 cam, 1 kızıltaş tozu, 3 demir külçesi:

```text
C C C
C K C
D D D
```

**Doomplayer Kumandası** — tek bir dikey sütunda 1 taş düğme, 1 kızıltaş tozu, 1 demir külçesi:

```text
T
K
D
```

`C` = cam, `K` = kızıltaş tozu, `D` = demir külçesi, `T` = taş düğme.

## Kaynak koddan derleme

Minecraft 1.21.1 modülleri dahil tüm projeyi derlemek için **JDK 21** kullan. 1.20.1 modülleri Java 17 bytecode hedefiyle derlenir. Projede Gradle **8.10.2** wrapper bulunduğundan ayrıca Gradle kurmak gerekmez. İlk derleme, bağımlılık depolarına erişim gerektirir.

Windows:

```powershell
.\gradlew.bat build
```

Linux veya macOS:

```sh
./gradlew build
```

Tek bir hedefi derlemek için, örneğin Fabric 1.20.1:

```sh
./gradlew :fabric-1.20.1:build
```

JAR dosyaları her modülün `build/libs/` klasörüne yazılır:

| Modül | Dosya |
| --- | --- |
| `fabric-1.20.1` | `doommc-fabric-1.20.1-1.0.0.jar` |
| `fabric-1.21.1` | `doommc-fabric-1.21.1-1.0.0.jar` |
| `forge-1.20.1` | `doommc-forge-1.20.1-1.0.0.jar` |
| `forge-1.21.1` | `doommc-forge-1.21.1-1.0.0.jar` |

## Proje yapısı

```text
common/src/main/java/       Ortak oyun motoru, haritalar, görseller, çeviriler ve durum paketleri
fabric-1.20.1/              Minecraft 1.20.1 Fabric entegrasyonu
fabric-1.21.1/              Minecraft 1.21.1 Fabric entegrasyonu
forge-1.20.1/               Minecraft 1.20.1 Forge entegrasyonu
forge-1.21.1/               Minecraft 1.21.1 Forge entegrasyonu
gradle/wrapper/             Gradle wrapper dosyaları
```

Ortak motor Minecraft'a bağımlı değildir. Her modül motoru; ekran yerleştirme, kumanda, ağ iletişimi ve dünyada görüntü çizme koduyla birlikte derler. Simülasyon sunucuda çalışır; istemciler gelen durum paketlerini kullanarak ekran görüntüsünü kendi taraflarında oluşturur.

Yerel Minecraft dünyaları, günlükler, Gradle önbelleği, derleme çıktıları ve özgün `kurulum/` paketleri sürüm kontrolüne alınmaz. İndirilebilir mod dosyaları Releases bölümünde tutulur.

## Lisans ve geliştirici

Geliştirici: **akadi**. Modun mevcut metadata dosyalarında belirtilen lisans **MIT**'dir; ayrıntılar için [LICENSE](LICENSE) dosyasına bak.

DoomMC, DOOM'dan esinlenen bağımsız bir hayran projesidir. Nişancı oyunu kendi görsellerini üretir ve Minecraft seslerini kullanır. id Software, Mojang veya Microsoft ile bağlantılı değildir.
