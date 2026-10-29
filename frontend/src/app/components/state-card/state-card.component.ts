import { Component, input } from '@angular/core';
import { CommonModule } from '@angular/common';

type State = 'empty' | 'loading' | 'unavailable';

@Component({
  selector: 'app-state-card',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="state-card" [class.empty]="mode()==='empty'" [class.loading]="mode()==='loading'" [class.unavailable]="mode()==='unavailable'">
      <div class="icon" *ngIf="mode()==='empty'">
        <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <rect x="3" y="3" width="18" height="18" rx="2"/>
          <line x1="9" y1="9" x2="15" y2="15"/><line x1="15" y1="9" x2="9" y2="15"/>
        </svg>
      </div>
      <div class="icon" *ngIf="mode()==='loading'">
        <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" class="spin">
          <path d="M12 2v4M12 18v4M4.93 4.93l2.83 2.83M16.24 16.24l2.83 2.83M2 12h4M18 12h4M4.93 19.07l2.83-2.83M16.24 7.76l2.83-2.83"/>
        </svg>
      </div>
      <div class="icon" *ngIf="mode()==='unavailable'">
        <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <circle cx="12" cy="12" r="10"/>
          <line x1="12" y1="8" x2="12" y2="12"/>
          <line x1="12" y1="16" x2="12.01" y2="16"/>
        </svg>
      </div>
      <p class="message">{{message()}}</p>
      <ng-content />
    </div>
  `,
  styleUrls: ['./state-card.component.css']
})
export class StateCardComponent {
  mode = input<State>('empty');
  message = input<string>('Nothing to show yet.');
}
