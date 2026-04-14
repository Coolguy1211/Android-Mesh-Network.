import RNS
import RNS.Interfaces
import threading
import time
import os

# This will be called from Kotlin to send packets to Reticulum
reticulum_instance = None
kotlin_bridge = None

class NearbyInterface(RNS.Interfaces.Interface.Interface):
    def __init__(self, owner, name):
        super().__init__()
        self.owner = owner
        self.name = name
        self.online = True
        self.bitrate = 1000000 

    def publish_packet(self, data):
        self.owner.inbound(data, self)

    def send(self, data):
        if kotlin_bridge:
            kotlin_bridge.sendNearbyPacket(data)

def start_reticulum(bridge_obj, config_path):
    global reticulum_instance, kotlin_bridge
    kotlin_bridge = bridge_obj
    
    # Ensure config directory exists
    if not os.path.exists(config_path):
        os.makedirs(config_path)
        
    config_file = os.path.join(config_path, "config")
    if not os.path.exists(config_file):
        with open(config_file, "w") as f:
            f.write("# Reticulum Config for MeshApp\n")
            f.write("[reticulum]\n")
            f.write("enable_transport = Yes\n\n")
            f.write("[interfaces]\n")
            f.write("  [[Local Interface]]\n")
            f.write("    type = AutoInterface\n")
            f.write("    interface_enabled = Yes\n")
    
    # Initialize Reticulum
    reticulum_instance = RNS.Reticulum(configdir=config_path)
    
    # Create our custom Nearby Interface and attach it
    nearby_if = NearbyInterface(reticulum_instance, "NearbyMesh")
    reticulum_instance.interfaces.append(nearby_if)
    
    RNS.log("Reticulum Mesh Bridge Started with Local Interface enabled")
    
    while True:
        time.sleep(1)

def inject_packet(data):
    if reticulum_instance:
        for interface in reticulum_instance.interfaces:
            if isinstance(interface, NearbyInterface):
                interface.publish_packet(data)
