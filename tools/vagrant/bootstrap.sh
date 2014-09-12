#!/usr/bin/env bash
#
# Prepares a virtual machine for running Clasp
#
# Intentionally uses ~, $HOME, and $USER so that the 
# same script can work for VirtualBox (username vagrant)
# and Amazon (username ubuntu)
#
# Add everything passed in the first argument to our 
# local environment. This is a hack to let us use 
# environment variables defined on the host inside the 
# guest machine
#
# Assumes clasp is synced at /clasp

# A shell provisioner is called multiple times
if [ ! -e "~/.firstboot" ]; then

  # Workaround mitchellh/vagrant#289
  echo "grub-pc grub-pc/install_devices multiselect     /dev/sda" | sudo debconf-set-selections

  # Install prerequisite tools
  echo "Installing prerequisites"
  sudo apt-get update
  sudo apt-get install -y git openjdk-7-jdk scala
  wget http://repo.scala-sbt.org/scalasbt/sbt-native-packages/org/scala-sbt/sbt/0.13.0/sbt.deb --no-verbose
  sudo dpkg -i sbt.deb  

  # Make project available
  ln -s /vagrant $HOME/clasp

  # Everyone gets SSH access to localhost
  echo "Setting up SSH access to localhost"
  ssh-keygen -t rsa -N '' -f ~/.ssh/id_rsa
  cat ~/.ssh/id_rsa.pub >> ~/.ssh/authorized_keys
  chmod 600 ~/.ssh/authorized_keys

  # Setup 
  echo "Installing software"
  cd $HOME/clasp
  sbt assembly

  sudo apt-get install nodejs npm
  cd www
  npm install
  
fi