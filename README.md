The free version of the ide on my phone does not allow you to create more than one java file (not counting MainActivity.java), so all of the important code is in one mess of a file. The free version also does not support git, and the project folder is in the app directory, which means that I would have to root my phone and void the warranty to get at it. I had to copy the code and send it to my computer, then create an Android Studio project to get it here. The master branch contains the code as I wrote it on my phone (with one change to how map.txt is loaded, because Android API 15 does not support java.nio.Files.readAllLines()) and the pc branch contains new changes.

The demo loads a map from "raycaster-demo-files/map.txt" in the phone's internal storage (create the folder "raycaster-demo-files," and add "map.txt" to it in the phone's root directory). The demo interprets each line of the file separately, and lines can have different prefixes that will determine how they are interpreted (note: spacing does not matter):


Example lines in map.txt:

"texture bricks = bricks0.jpg" - Loads an image named "bricks0.jpg" and initializes it as "bricks" in the file. Textures must be initialized before they can be used for anything else. The root directory for textures is map.txt's parent directory.

"floortex dirt" - Sets the floor texture to "dirt," which would have been initialized earlier (example: "texture dirt = cool_dirt.png")
"ceiltex blue_stone" - Sets the ceiling texture to "blue_stone". The floor and ceiling textures should have square resolutions, because they will be tiled on the floor and ceiling. Each 1x1 square on the floor or ceiling will contain a texture.

"startpos 50, 50" - Sets the player's start position to 50, 50

"line 4, 2, 1, 3, red_bricks" - Creates a wall from (4, 2) to (1, 3) with texture "red_bricks"
"line 1.5, 2, 4.5, 2, 3, 1, simonewall" - Creates a wall from (1.5, 2) to (4.5, 2) with texture "simonewall", tiled horizontally 3 times and vertically 1 time. If the tile arguments are omitted, the texture will be stretched to fit the wall.
