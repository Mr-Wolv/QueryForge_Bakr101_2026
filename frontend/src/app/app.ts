import { Component, inject, OnInit } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ThemeService } from './core/theme/theme.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet],
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App implements OnInit {
  private theme = inject(ThemeService);

  ngOnInit(): void {
    this.theme.apply(this.theme.current);
  }

  toggleTheme(): void {
    this.theme.toggle();
  }

  themeLabel(): string {
    return this.theme.current === 'light' ? 'Switch to dark mode' : 'Switch to light mode';
  }

  themeIcon(): string {
    return this.theme.current === 'light' ? '☾' : '☀';
  }
}
