import { Component, input, OnChanges, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ChartDataPoint } from '../../services/api.service';

@Component({
  selector: 'app-latency-chart',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="chart" *ngIf="data().length > 0">
      <div class="bar-group" *ngFor="let d of data(); let i = index">
        <div class="bar-wrap">
          <div class="bar" [style.height.%]="barHeight(d.value)" [style.background]="barColor(i)"></div>
        </div>
        <span class="label">{{d.label}}</span>
        <span class="value">{{d.value | number:'1.0-0'}}</span>
      </div>
    </div>
  `,
  styleUrls: ['./latency-chart.component.css']
})
export class LatencyChartComponent implements OnChanges {
  data = input.required<ChartDataPoint[]>();

  ngOnChanges(changes: SimpleChanges) {
    // reactive via binding; nothing extra needed
  }

  max(): number {
    const vals = this.data().map(d => d.value);
    return vals.length ? Math.max(...vals) : 1;
  }

  barHeight(value: number): number {
    const m = this.max();
    if (m <= 0) return 0;
    return Math.max((value / m) * 100, 2);
  }

  barColor(index: number): string {
    const colors = ['#111', '#333', '#555', '#777', '#999'];
    return colors[index % colors.length];
  }
}
