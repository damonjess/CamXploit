import sys
import io
import threading
from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.textinput import TextInput
from kivy.uix.button import Button
from kivy.uix.scrollview import ScrollView
from kivy.uix.label import Label
from kivy.clock import Clock
from kivy.core.window import Window

# Import the original script logic
import CamXploit

class MobileCamXploit(App):
    def build(self):
        Window.clearcolor = (0.1, 0.1, 0.1, 1)
        self.title = "CamXploit Mobile"

        layout = BoxLayout(orientation='vertical', padding=20, spacing=20)

        # IP Input Box
        self.ip_input = TextInput(
            hint_text='Enter IP Address (e.g., 1.2.3.4)',
            multiline=False,
            size_hint_y=None,
            height='50dp',
            background_color=(0.2, 0.2, 0.2, 1),
            foreground_color=(1, 1, 1, 1),
            padding=[10, 10, 10, 10]
        )
        layout.add_widget(self.ip_input)

        # Start Button
        self.scan_btn = Button(
            text='START SCAN',
            size_hint_y=None,
            height='60dp',
            background_color=(0.2, 0.6, 0.2, 1),
            color=(1, 1, 1, 1),
            bold=True
        )
        self.scan_btn.bind(on_press=self.start_scan)
        layout.add_widget(self.scan_btn)

        # Terminal-style Progress View
        self.scroll = ScrollView(
            size_hint=(1, 1),
            bar_width='10dp',
            scroll_type=['bars', 'content']
        )

        self.terminal = Label(
            text="[color=00ff00]Welcome to CamXploit Mobile[/color]\nReady for input...\n",
            markup=True,
            size_hint_y=None,
            halign='left',
            valign='top',
            font_name='Roboto', # Standard Kivy font, usually looks okay for terminal
            color=(0.9, 0.9, 0.9, 1)
        )
        self.terminal.bind(texture_size=self.terminal.setter('size'))
        self.scroll.add_widget(self.terminal)
        layout.add_widget(self.scroll)

        return layout

    def start_scan(self, instance):
        ip = self.ip_input.text.strip()
        if not ip:
            self.update_terminal("[color=ff0000]Error: Please enter an IP address[/color]\n")
            return

        self.scan_btn.disabled = True
        self.terminal.text = f"[color=00ffff]>>> Initializing scan for {ip}...[/color]\n"

        # Run scan in a separate thread to keep UI responsive
        threading.Thread(target=self.run_logic, args=(ip,), daemon=True).start()

    def update_terminal(self, text):
        # ANSI to simple markup (very basic)
        # Note: CamXploit.py strips colors if sys.stdout.isatty() is False
        # which it will be here.
        self.terminal.text += text
        # Scroll to bottom
        Clock.schedule_once(lambda dt: self.set_scroll_bottom())

    def set_scroll_bottom(self):
        self.scroll.scroll_y = 0

    def run_logic(self, ip):
        # Redirect stdout to capture prints from CamXploit.py
        class StdoutRedirector(io.StringIO):
            def __init__(self, callback):
                super().__init__()
                self.callback = callback
            def write(self, s):
                if s.strip() or s == '\n':
                    Clock.schedule_once(lambda dt: self.callback(s))
            def flush(self):
                pass

        old_stdout = sys.stdout
        sys.stdout = StdoutRedirector(self.update_terminal)

        try:
            CamXploit.main(ip_input=ip)
        except Exception as e:
            self.update_terminal(f"\n[color=ff0000]Application Error: {str(e)}[/color]\n")
        finally:
            sys.stdout = old_stdout
            Clock.schedule_once(lambda dt: self.enable_button())

    def enable_button(self):
        self.scan_btn.disabled = False
        self.update_terminal("\n[color=00ff00]Scan Finished.[/color]\n")

if __name__ == '__main__':
    MobileCamXploit().run()
