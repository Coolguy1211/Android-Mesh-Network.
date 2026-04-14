import RNS
import os
import threading
import time

class MeshDesktopRNS:
    def __init__(self, config_dir="./reticulum_config"):
        if not os.path.exists(config_dir):
            os.makedirs(config_dir)
            
        config_file = os.path.join(config_dir, "config")
        if not os.path.exists(config_file):
            with open(config_file, "w") as f:
                f.write("[reticulum]\n")
                f.write("enable_transport = Yes\n\n")
                f.write("[interfaces]\n")
                f.write("  [[Local Interface]]\n")
                f.write("    type = AutoInterface\n")
                f.write("    interface_enabled = Yes\n")

        self.reticulum = RNS.Reticulum(configdir=config_dir)
        self.identity = RNS.Identity()
        self.destination = RNS.Destination(
            self.identity,
            RNS.Destination.IN,
            RNS.Destination.SINGLE,
            "MeshDesktop",
            "ControlCenter"
        )
        
        self.chat_history = []
        self.destination.set_packet_callback(self.receive_packet)
        
        threading.Thread(target=self.keep_alive, daemon=True).start()

    def receive_packet(self, data, packet):
        try:
            msg = data.decode("utf-8")
            self.chat_history.append(f"Mesh: {msg}")
        except:
            pass

    def send_broadcast(self, msg):
        # In Reticulum, we usually send to specific destinations, 
        # but for a simple "Mesh Chat" we can use a shared destination name.
        pass

    def keep_alive(self):
        while True:
            time.sleep(1)

def start_rns():
    return MeshDesktopRNS()
