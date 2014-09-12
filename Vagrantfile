# -*- mode: ruby -*-
# vi: set ft=ruby :

require_relative 'tools/vagrant/checks'

provider = get_provider
check_provider_needs(provider)

Vagrant.configure("2") do |config|

  config.vm.provision "shell" do |sh|
    sh.path = "tools/vagrant/bootstrap.sh"
    sh.privileged = false
  end

  config.vm.provider :virtualbox do |vb, override|
    override.vm.hostname = "Clasp"

    override.vm.box = "ubuntu/trusty64"
    if ENV.fetch('CL_VM_ARCH','64') == "32"
      override.vm.box = "ubuntu/trusty32"
    end
    
    if ENV.fetch('CL_SHOW_VM', false)
      vb.gui = true
    end

    vb.memory = ENV.fetch('CL_VB_MEM', 2048)
    vb.cpus = ENV.fetch('CL_VB_CPU', 2)

    override.vm.network :forwarded_port, guest: 9090, host: 9090
  end
end