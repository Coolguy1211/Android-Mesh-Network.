import tkinter as tk
from tkinter import ttk, scrolledtext, messagebox
import requests
import threading
import time
import sys
import os
import pystray
from PIL import Image, ImageDraw

# --- Configuration ---
GATEWAY_IP = "192.168.43.1"  # Default Android Hotspot Gateway IP
API_BASE = f"http://{GATEWAY_IP}:8080/api"

class MeshDesktopApp:
    def __init__(self, root):
        self.root = root
        self.root.title("Mesh Control Center")
        self.root.geometry("600x500")
        
        self.chat_messages = []
        self.is_running = True
        
        self.setup_ui()
        self.start_poll_threads()

    def setup_ui(self):
        # Tabs
        self.notebook = ttk.Notebook(self.root)
        self.notebook.pack(expand=True, fill='both', padx=10, pady=10)

        # Chat Tab
        self.chat_frame = ttk.Frame(self.notebook)
        self.notebook.add(self.chat_frame, text="💬 Mesh Chat")

        self.chat_display = scrolledtext.ScrolledText(self.chat_frame, state='disabled', height=15)
        self.chat_display.pack(expand=True, fill='both', padx=5, pady=5)

        self.input_frame = ttk.Frame(self.chat_frame)
        self.input_frame.pack(fill='x', padx=5, pady=5)

        self.msg_entry = ttk.Entry(self.input_frame)
        self.msg_entry.pack(side='left', expand=True, fill='x')
        self.msg_entry.bind("<Return>", lambda e: self.send_chat())

        self.send_btn = ttk.Button(self.input_frame, text="Send", command=self.send_chat)
        self.send_btn.pack(side='right', padx=5)

        # Status Tab
        self.status_frame = ttk.Frame(self.notebook)
        self.notebook.add(self.status_frame, text="📊 Topology & Routes")

        self.status_display = scrolledtext.ScrolledText(self.status_frame, state='disabled')
        self.status_display.pack(expand=True, fill='both', padx=5, pady=5)

        # Settings Tab
        self.settings_frame = ttk.Frame(self.notebook)
        self.notebook.add(self.settings_frame, text="⚙️ Settings")
        
        ttk.Label(self.settings_frame, text="Gateway IP:").pack(pady=5)
        self.ip_entry = ttk.Entry(self.settings_frame)
        self.ip_entry.insert(0, GATEWAY_IP)
        self.ip_entry.pack(pady=5)
        
        ttk.Button(self.settings_frame, text="Update Connection", command=self.update_ip).pack(pady=10)

    def update_ip(self):
        global API_BASE
        new_ip = self.ip_entry.get()
        API_BASE = f"http://{new_ip}:8080/api"
        messagebox.showinfo("Settings", f"Connected to {new_ip}")

    def send_chat(self):
        msg = self.msg_entry.get()
        if not msg: return
        self.msg_entry.delete(0, tk.END)
        
        def do_send():
            try:
                requests.post(f"{API_BASE}/send", data={'msg': msg}, timeout=2)
            except:
                pass
        threading.Thread(target=do_send).start()

    def start_poll_threads(self):
        threading.Thread(target=self.poll_chat, daemon=True).start()
        threading.Thread(target=self.poll_status, daemon=True).start()

    def poll_chat(self):
        while self.is_running:
            try:
                res = requests.get(f"{API_BASE}/chat", timeout=2)
                if res.status_code == 200:
                    new_msgs = res.json()
                    if new_msgs != self.chat_messages:
                        self.chat_messages = new_msgs
                        self.update_chat_display()
            except:
                pass
            time.sleep(2)

    def poll_status(self):
        while self.is_running:
            try:
                status_res = requests.get(f"{API_BASE}/status", timeout=2)
                routes_res = requests.get(f"{API_BASE}/routes", timeout=2)
                
                text = "--- SYSTEM STATUS ---\n"
                if status_res.status_code == 200:
                    text += f"{status_res.text}\n\n"
                
                text += "--- ACTIVE ROUTES ---\n"
                if routes_res.status_code == 200:
                    routes = routes_res.json()
                    for r in routes:
                        text += f"-> {r['destination']} via {r['nextHop']} ({r['hops']} hops) [{r['role']}]\n"
                
                self.root.after(0, lambda t=text: self.set_status_text(t))
            except:
                pass
            time.sleep(3)

    def update_chat_display(self):
        self.chat_display.configure(state='normal')
        self.chat_display.delete('1.0', tk.END)
        for m in self.chat_messages:
            self.chat_display.insert(tk.END, m + "\n")
        self.chat_display.configure(state='disabled')
        self.chat_display.see(tk.END)

    def set_status_text(self, text):
        self.status_display.configure(state='normal')
        self.status_display.delete('1.0', tk.END)
        self.status_display.insert(tk.END, text)
        self.status_display.configure(state='disabled')

def create_image():
    # Generate a simple blue icon for the tray
    image = Image.new('RGB', (64, 64), (255, 255, 255))
    dc = ImageDraw.Draw(image)
    dc.rectangle((16, 16, 48, 48), fill=(21, 101, 192))
    return image

def on_quit(icon, item):
    icon.stop()
    os._exit(0)

def show_window(icon, item):
    icon.visible = True
    # In a real tray app, we'd deiconify the tkinter root here
    pass

if __name__ == "__main__":
    root = tk.Tk()
    app = MeshDesktopApp(root)
    
    # Tray Icon setup
    icon = pystray.Icon("MeshApp", create_image(), "Mesh Control Center", menu=pystray.Menu(
        pystray.MenuItem("Open Dashboard", show_window),
        pystray.MenuItem("Quit", on_quit)
    ))
    
    # Start tray in a separate thread
    threading.Thread(target=icon.run, daemon=True).start()
    
    root.mainloop()
