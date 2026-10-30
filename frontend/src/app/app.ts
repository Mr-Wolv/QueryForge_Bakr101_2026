import { Component, inject, OnInit } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { ThemeService } from './core/theme/theme.service';
import { AuthService } from './core/auth/auth.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App implements OnInit {
  readonly theme = inject(ThemeService);
  readonly auth = inject(AuthService);

  ngOnInit(): void {
    this.theme.apply(this.theme.current);
    this.auth.load();
  }

  toggleTheme(): void {
    this.theme.toggle();
  }

  themeLabel(): string {
    return this.theme.current === 'light' ? 'Switch to dark mode' : 'Switch to light mode';
  }
}
