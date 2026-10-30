import { Component, input, OnChanges, SimpleChanges, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { BaseChartDirective, provideCharts, withDefaultRegisterables } from 'ng2-charts';
import { ChartDataPoint } from '../../services/api.service';

@Component({
  selector: 'app-latency-chart',
  standalone: true,
  imports: [CommonModule, BaseChartDirective],
  providers: [provideCharts(withDefaultRegisterables())],
  template: `
    <div class="chart" *ngIf="chartData.datasets[0].data.length > 0; else empty">
      <canvas baseChart
        [type]="chartType"
        [data]="chartData"
        [options]="chartOptions">
      </canvas>
    </div>
    <ng-template #empty>
      <div class="empty">No data available</div>
    </ng-template>
  `,
  styles: [`
    .chart {
      padding: var(--space-4) 0;
      max-height: 280px;
    }
    .empty {
      padding: var(--space-6) 0;
      text-align: center;
      color: var(--color-text-muted);
      font-size: var(--text-sm);
    }
  `]
})
export class LatencyChartComponent implements OnInit, OnChanges {
  data = input.required<ChartDataPoint[]>();
  readonly chartType = 'bar' as const;

  chartData = {
    labels: [] as string[],
    datasets: [{
      data: [] as number[],
      backgroundColor: '#1a1a1a',
      hoverBackgroundColor: '#1a1a1a',
      borderRadius: 4,
      borderSkipped: false,
      barPercentage: 0.7,
      categoryPercentage: 0.8
    }]
  };

  chartOptions = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { display: false },
      tooltip: {
        backgroundColor: '#ffffff',
        titleColor: '#111111',
        bodyColor: '#555555',
        borderColor: '#e5e5e5',
        borderWidth: 1,
        padding: 10,
        cornerRadius: 6
      }
    },
    scales: {
      x: {
        grid: { display: false },
        ticks: {
          color: '#999999',
          font: { size: 11 }
        }
      },
      y: {
        grid: {
          color: '#f0f0f0'
        },
        ticks: {
          color: '#999999',
          font: { size: 11 }
        },
        beginAtZero: true
      }
    }
  };

  ngOnInit(): void {
    this.updateData();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['data'] && !changes['data'].isFirstChange()) {
      this.updateData();
    }
  }

  private updateData() {
    const points = this.data();
    this.chartData.labels = points.map(p => p.label);
    this.chartData.datasets[0].data = points.map(p => p.value);
  }
}
