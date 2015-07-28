# <a name="readme"></a><a name="Readme"></a>WRF-Runner
A simple script for running WRF.  This is written in *Java 8* and is designed for use on personal systems - due to the nature of large-scale systems, there is no guarantee that this will be compatible with its job-management system.

## Usage
### Setup
#### <a name="rp"></a>Required programs (these are all command line utilities)

* wget
* ant
* javac (via the [Java 8 JDK](http://www.oracle.com/technetwork/java/javase/downloads/index.html))

If you don't have these, see [Getting the Required Programs](#gtrp) for how to get them.

#### <a name="rl"></a>Required Libraries

* [Lexer](https://github.com/Toberumono/Lexer)
* [JSON Library](https://github.com/Toberumono/JSON-Library)
* [Namelist Parser](https://github.com/Toberumono/Namelist-Parser)
* [Structures](https://github.com/Toberumono/Structures)

If you don't have these, see [Getting the Required Libraries](#gtrl) for how to get them.

#### Compiling the Program
1. Make sure that you have the [Required Programs](#rp) and [Required Libraries](#rl).  If you don't, follow the directions in [Getting the Required Programs](#gtrp) and [Getting the Required Libraries](#gtrl) respectively.
2. cd into the directory with all of the .jars from the [Required Libraries](#rl)
3. Run this script:

```bash
mkdir WRF-Runner; cd WRF-Runner; git init; git pull https://github.com/Toberumono/WRF-Runner.git; ant;
```

### Running a WRF process
#### Configuring
<i>Note</i>: This describes only the minimal amount of configuration required for a successful run.  There are several options not visited here.</br>
<i>See [Description of Configuration Variables](#docv) for information on each option.</i>

1. This program downloads data that needs the NAM Vtable in WPS by default.  To ensure that the correct Vtable is used, run: `ln -sf ./ungrib/Variable_Tables/Vtable.NAM ./Vtable` in the WPS installation directory.
2. Edit the WRF and WPS Namelist files such that they could be used for a single run (basically, set everything other than the start and end dates and times in both the Namelist.input and Namelist.wps files)
3. Open the configuration file (configuration.json) in the directory into which you pulled the WRF Runner project data.
	1. if you want to use a different file, just copy the contents of configuration.json into it before continuing and remember to change the configuration file path in the run step.
4. Configure the parallelization options in general->parallel:
	1. If you did not compile WRF in DMPAR mode, set "is-dmpar" to false and continue to step 3.
	2. Set "processors" to the number of processors you would like to allow WRF to use.
5. Configure paths:
	1. Set the "wrf" path to the *run* directory of your WRF installation.
	2. Set the "wps" path to the root directory of your WPS installation.
	3. Set the "working" path to an empty or non-existent directory.
	4. Set the "grib_data" path to an empty directory, preferably a sub-directory of the working directory (either way, this requires the full path)
6. Configure timing:
	1. Go through and set the variables as appropriate.  If you are unsure about "rounding", leave it enabled.  (Actually, in the default implementation of the wget function, this *must* be enabled)
	2. Configure the offset if you so desire, or disable it.  It is not required by any components of the script.
7. <a name="cc"></a>Configure commands:
	1. To get the paths to each command, run the following:</br>
		```
		echo -e "\t\t\"bash\" : \"$(which bash)\",\n\t\t\"rm\" : \"$(which rm)\",\n\t\t\"wget\" : \"$(which wget)\""
		```
	2. Paste the output of that command into the "commands" section.
8. That's it.

#### Running
1. cd to the directory into which you pulled the WRF Runner repository.
2. run `java -jar WRFRunner.jar configuration.json` (where configuration.json is the path to your configuration file).

## Help
### <a name="docv"></a>Description of Configuration Variables

+ general
	+ features: These are toggles for the pieces of the script, mostly useful in debugging.  Generally, the first three must be enabled for an actual run.
		- wget: Toggle the wget step
		- wps: Toggle the WPS step
		- wrf: Toggle the WRF step
		- cleanup: If this is true, then the script will automatically delete downloaded or other intermediate files that are no longer needed.
	+ parallel:
		- is-dmpar: This tells the script whether WRF and WPS were set up with DMPAR mode.  This is effectively the toggle for all parallel components.
		- boot-lam: True indicates that the mpich call should include the boot flag.  This should only be used on personal machines that will not have another mpich process running on them.
		- processors: The number of processors to allow WRF to use.  If you intend to continue using the computer on which you are running the simulation while the simulation is in progress, leave your system at least 2 processors.
	+ wait-for-WRF: True indicates that the script should wait for WRF to complete.  This *must* be true for it to perform the final stage of cleanup.  Otherwise, this is a matter of preference.
+ paths: Absolute paths to the executable directories for WRF and WPS as well as the working and grib data directories.  These must not end in '/'.  
	- wrf: The path to the WRF *run* directory.
	- wps: The path to the WPS *root* directory.
	- working: A path to an arbitrary, preferably empty folder into which temporary files can be placed.
	- grib_data: A path to an arbitrary, preferably empty folder into which downloaded grib data can be placed.
+ timing: Settings for calculating the appropriate start and end times of the simulation.
	+ rounding: This is used to set the first non-zero field in the time settings and give it a simple offset.
		- enabled: Toggles this feature
		- diff: A quick offset setting, can be previous, current, or next.  Generally, leaving this on current is advisable.
		- magnitude: The first non-zero value.  While this can be second, minute, hour, day, month, or year, day is generally recommended.
	+ offset: These are used to fine-tune the start time of the simulation.  These values *can* be negative.
		- enabled: Toggles this feature
		- days: Number of days to shift the start time by.  This field's magnitude should generally never exceed 2.
		- hours: Number of hours to shift the start time by.  This field's magnitude should generally never exceed 48.
		- minutes: While it is possible to set an offset with minutes, this is highly inadvisable and will cause the simulation to fail with the default dataset.
		- seconds: While it is possible to set an offset with seconds, this is highly inadvisable and will cause the simulation to fail with the default dataset.
+ commands: Paths to the executables used in this script.  These can different across systems.  See [Configuring commands](#cc) for a script that will print out these values such they can by copy-pasted in.
	- bash
	- rm
	- wget


### <a name="gtrp"></a>Getting the Required Programs

- Linux (note: you may need to replace the names of the downloaded files or the directories they unpacked to to match the versions that you downloaded):
	1. Download the appropriate [Java 8 JDK](http://www.oracle.com/technetwork/java/javase/downloads/index.html).
	2. Unpack the archive into /usr/lib/jvm.  Run: `sudo mkdir /usr/lib/jvm; sudo tar zxvf jdk-8u51-linux-x64.tar.gz -C /usr/lib/jvm`
	3. Link the executables. Run: `sudo ln -s /usr/lib/jvm/jdk1.8.0_51/bin/java /usr/bin/java; sudo ln -s /usr/lib/jvm/jdk1.8.0_51/bin/javac /usr/bin/javac`.
	4. Download the appropriate version of [ANT](https://ant.apache.org/bindownload.cgi).
	5. Unpack the archive into /usr/local/ant.  Run: `tar zxvf apache-ant-1.9.6-bin.tar.gz; sudo mv apache-ant-1.9.6 /usr/local/ant`.
	6. Link the executables. Run: `sudo ln -s /usr/local/ant/bin/ant /usr/bin/ant`.
	7. Install wget and git. Run: `sudo apt-get install build-essential wget git`.
- Mac:
	1. install the appropriate [Java 8 JDK](http://www.oracle.com/technetwork/java/javase/downloads/index.html).
	2. install [Homebrew](http://brew.sh/).
	3. run `brew install wget`.
	4. run `brew install ant`.
	5. run `brew install git`.

### <a name="gtrl"></a>Getting the Required Libraries

<b>This script only applies to Unix-based operating systems (Mac OSX, Linux, Unix, probably some others)</b></br>
Further, this script assumes you have the [Required Programs](#rp) installed.  If you don't, follow the instructions in [Getting the Required Programs](#gtrp) first.

1. cd into the directory in which you would like to build the libraries (Creating an empty directory is strongly recommended).
2. Run the following in terminal (you can just copy and paste it, but remember to hit enter after pasting it in even if stuff starts running):
```bash
mkdir Structures;
cd Structures;
git init;
git pull https://github.com/Toberumono/Structures.git;
ant;
cd ../;
mkdir Lexer;
cd Lexer;
git init;
git pull https://github.com/Toberumono/Lexer.git;
ant;
cd ../;
mkdir JSON-Library;
cd JSON-Library;
git init;
git pull https://github.com/Toberumono/JSON-Library.git;
ant;
cd ../;
mkdir Namelist-Parser;
cd Namelist-Parser;
git init;
git pull https://github.com/Toberumono/Namelist-Parser.git;
ant;
cd ../;
```

### <a name="ctld"></a>Changing the Library Directory
If one of my projects uses additional libraries, I include the line `<property name="libs" location="../" />` towards the top.
Therefore, to change the library directory, simply change the value of location for that property.
<b>It is tempting to change the</b>
```xml
<fileset id="libraries" dir="${libs}">
	<i>library names</i>
</fileset>
```
<b>block, but do not do so</b>.