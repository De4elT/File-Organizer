File Organizer to aplikacja desktopowa napisana w Kotlinie z użyciem JavaFX, przeznaczona do szybkiego sortowania, organizowania i wyszukiwania plików na komputerze,
dla różnych systemów operacyjnych obecnie dostosowana głównie do Windowsa.

Najważniejsze cechy
Pozwala automatycznie porządkować pliki z wybranego folderu do podfolderów według rozszerzenia lub własnych kategorii.

Umożliwia wyszukiwanie plików po wielu kryteriach jednocześnie:

Nazwa pliku (wzorce SQL LIKE, np. %raport%)

Rozmiar (od – do, różne jednostki)

Data utworzenia lub modyfikacji (od – do)

Prezentuje dokładną lokalizację każdego znalezionego pliku w postaci ścieżki względnej względem folderu głównego.

Obsługuje szybkie kopiowanie lub przenoszenie wybranych plików do automatycznie utworzonych folderów kategorii (np. dokumenty, obrazy, archiwa).

Konfiguracja kategorii plików oraz działania programu odbywa się przez prosty plik file-organizer.cfg (tworzony automatycznie przy pierwszym uruchomieniu).

Obsługuje tryb „Zachowaj oryginał” (kopiowanie zamiast przenoszenia) oraz opcję pomijania plików wykonywalnych (.exe, .lnk).

Działanie programu
Po uruchomieniu aplikacji wyświetlane jest okno, w którym można wybrać katalog do przeszukania (przyciski: Choose, Documents, Downloads, Desktop).

Po załadowaniu folderu prezentowana jest lista wszystkich znalezionych plików wraz z ich nazwą, rozmiarem, datą oraz ścieżką względną.

Dostępne są pola filtrów umożliwiające zawężenie widoku do plików spełniających kilka warunków jednocześnie (nazwa, rozmiar, data).

Po zaznaczeniu wybranych plików (można zaznaczać pojedynczo lub wszystkie naraz – klikając pole wyboru w nagłówku kolumny) oraz ustawieniu opcji kopiowania/przenoszenia można wykonać operację Organize selected.

Pliki zostaną przeniesione lub skopiowane do podfolderów, których strukturę i rozszerzenia można konfigurować w pliku konfiguracyjnym.
W katalogu docelowym zostanie utworzony plik raportu (organizer_report-YYYY-MM-DD.txt) z listą wykonanych operacji.

Konfiguracja
Plik file-organizer.cfg generuje się automatycznie w folderze, z którego uruchamiany jest program (tam gdzie plik JAR).

Format pliku to klasyczny .properties.
Przykład:

bash
Copy
Edit
documents/pdf=pdf,doc,docx,txt
images/photos=jpg,jpeg,png,webp
presentations/ppt=ppt,pptx,odp
archives/all=zip,rar,7z,tar,gz
exe=exe,msi
keepOriginal=false
skipExe=true
useCustomDestination=false
customDestination=
# Można dodać własne kategorie według wzoru (każdy w nowej linii):
# music/mp3=mp3,wav,flac
Dodatkowe kategorie (np. „music/mp3=mp3,wav”) można dopisywać ręcznie, kierując się schematem:
nazwa_folderu/nazwa_podfolderu=rozszerzenie1,rozszerzenie2,...
Linia z # na początku jest komentarzem.

Wymagania
Java 17 lub nowsza (zalecane OpenJDK 17+).


Sposób uruchomienia
Zbudować plik JAR (./gradlew shadowJar lub inny sposób dla projektu Kotlin/JavaFX).

Umieścić wygenerowany plik JAR oraz ewentualnie plik file-organizer.cfg w jednym folderze.

Uruchomić aplikację poleceniem:

nginx
Copy
Edit
java -jar file-organizer.jar
lub kliknąć dwukrotnie w systemie.

Notatki
Aplikacja nie usuwa żadnych plików – zawsze wykonuje operacje kopiowania lub przenoszenia do podfolderów.

Program pomija pliki bez uprawnień odczytu, informując o ich liczbie w osobnym oknie.

Po filtrowaniu, tylko wyświetlane i zaznaczone pliki będą uwzględnione przy organizacji.