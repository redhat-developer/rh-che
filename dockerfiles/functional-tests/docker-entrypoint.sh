#!/bin/bash
echo -n Running Xvfb...
nohup chromedriver &
/usr/bin/Xvfb :99 -screen 0 1920x1080x24 +extension RANDR
