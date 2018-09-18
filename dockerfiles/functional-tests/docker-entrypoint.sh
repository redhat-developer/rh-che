#!/bin/bash
echo -n Running Xvfb...
nohup chromedriver &
/usr/bin/Xvfb :99 -screen 0 1024x768x24 +extension RANDR
