Fiji distribution from https://github.com/fiji/fiji:

[ Fiji Is Just ImageJ ]

Fiji is a "batteries-included" distribution of ImageJ—a popular, free scientific image processing application—which includes a lot of plugins organized into a coherent menu structure. Fiji compares to ImageJ as Ubuntu compares to Linux.

The main focus of Fiji is to assist research in life sciences.

At the moment, the following platforms are supported:

Windows Intel 32-bit/64-bit
Linux Intel 32-bit/64-bit
MacOSX Intel 32-bit/64-bit (partial support for PowerPC 32-bit)
all platforms supporting Java and a POSIX shell, via bin/ImageJ.sh
The setup is as easy as unpacking the portable archive and double-clicking the ImageJ launcher.

Fiji is intended to be the most painless, easy, quick and convenient way to install ImageJ and plugins and keep everything up-to-date.

Usage

Fiji is meant to be distributed without source, to make the download as small as possible. In the basic version, Fiji is a portable application, i.e. it should run wherever you copy it.

The starting point is the ImageJ launcher, which will launch Java, set up the environment, and call ImageJ.

To pass arguments to ImageJ, just specify them on the command line.

To pass arguments to the Java Virtual Machine, specify them on the command line, separating them from the ImageJ arguments (if any) with a --. In other words, if you want to override the memory setting, call Fiji like this:

$ ./ImageJ-linux32 -Xmx128m --
Open Source

We are dedicated to open source. Not only does open source allow other developers to port the application to new platforms that the original authors did not begin to think of, it allows scientists to study the code to understand the inner workings of the algorithms used, and it permits others to use the program in totally new ways, and enhance it in all imaginable ways.

Therefore, the majority of Fiji is licensed under the GNU Public License version 2. Exceptions are listed in the LICENSES file.

Fiji's source code is split up into a main repository, containing the top-level project and support scripts, while all components live in their own repositories in the Fiji organization on GitHub. As a rule of thumb: the file name and the project name correspond pretty well, e.g. fiji-compat.jar is maintained in fiji-compat.

Participating

Pull Requests are very welcome!

See the Contributing page of the ImageJ wiki.

Authors

Fiji was created and is maintained by Johannes Schindelin, ImageJ 1.x was created and is maintained by Wayne Rasband, ImageJ2 was created and is maintained and actively developed by Curtis Rueden. For a list of most recent contributors, please refer to the Contributors page of the ImageJ wiki.

Thanks

We are very grateful to Wayne Rasband, who is not only a very dedicated developer of ImageJ 1.x; he also fosters an active and friendly community around ImageJ.

We are especially grateful to be part of an outstanding community who is active, friendly and helping to scientists understanding and analysing images every day.

Oh, and Fiji is also an island. We just wanted to let you know.



It also includes two plugins from BIG (ECOLE POLYTECHNIQEU FEDERALE DE LAUSANNE):

* A Java package for running ImageJ and Fiji within Matlab: MIJ from 
Daniel Sage, Dimiter Prodanov, Jean-Yves Tinevez and Johannes Schindelin, " MIJ: Making Interoperability Between ImageJ and Matlab Possible" ImageJ User & Developer Conference, 24-26 October 2012, Luxembourg.

* Bi-Exponential Edge-Preserving Smoother
P. Thévenaz, D. Sage, M. Unser, "Bi-Exponential Edge-Preserving Smoother" IEEE Transactions on Image Processing, vol. 21, no. 9, pp. 3924-3936, September 2012.

