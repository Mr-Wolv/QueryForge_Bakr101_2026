import { Component, OnDestroy, OnInit, ViewChild, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { BaseChartDirective, provideCharts, withDefaultRegisterables } from 'ng2-charts';
import { Plugin } from 'chart.js';
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
        [options]="chartOptions"
        [plugins]="plugins">
      </canvas>
    </div>
    <ng-template #empty>
      <div class="empty">No data available</div>
    </ng-template>
  `,
  styles: [`
    .chart {
      padding: var(--space-4) 0;
      max-height: 300px;
    }
    .empty {
      padding: var(--space-6) 0;
      text-align: center;
      color: var(--color-text-muted);
      font-size: var(--text-sm);
    }
  `]
})
export class LatencyChartComponent implements OnInit, OnDestroy {
  data = input.required<ChartDataPoint[]>();
  readonly chartType = 'bar' as const;

  chartData = this.buildChartData([]);
  chartOptions = this.buildOptions();

  /** Draws the rounded millisecond value above every bar. */
  readonly plugins: Plugin[] = [{
    id: 'qfValueLabels',
    afterDatasetsDraw: (chart) => {
      const { ctx } = chart;
      const meta = chart.getDatasetMeta(0);
      ctx.save();
      ctx.font = '600 11px Inter, system-ui, sans-serif';
      ctx.textAlign = 'center';
      ctx.textBaseline = 'bottom';
      ctx.fillStyle = this.palette().label;
      meta.data.forEach((bar, index) => {
        const value = chart.data.datasets[0]?.data[index];
        if (typeof value !== 'number') return;
        ctx.fillText(this.format(value), bar.x, bar.y - 6);
      });
      ctx.restore();
    }
  }];

  @ViewChild(BaseChartDirective) private chartDirective?: BaseChartDirective;

  private readonly onThemeChange = () => this.refresh();

  ngOnInit(): void {
    document.addEventListener('qf:theme', this.onThemeChange);
    this.updateData();
  }

  ngOnDestroy(): void {
    document.removeEventListener('qf:theme', this.onThemeChange);
  }

  private updateData(): void {
    this.chartData = this.buildChartData(this.data());
  }

  private refresh(): void {
    const colors = this.palette();
    this.chartOptions = this.buildOptions();
    this.chartData = this.buildChartData(this.data());

    // The canvas is painted outside Angular's change detection, so repaint the
    // live chart instance directly after a theme switch.
    const chart = this.chartDirective?.chart as any;
    if (!chart) return;
    const dataset = chart.data.datasets[0];
    dataset.backgroundColor = colors.bars;
    dataset.hoverBackgroundColor = colors.bars;
    chart.options.plugins.tooltip.backgroundColor = colors.tooltipBg;
    chart.options.plugins.tooltip.titleColor = colors.tooltipText;
    chart.options.plugins.tooltip.bodyColor = colors.tooltipText;
    chart.options.plugins.tooltip.borderColor = colors.tooltipBorder;
    chart.options.scales.x.ticks.color = colors.tick;
    chart.options.scales.x.border.color = colors.grid;
    chart.options.scales.y.ticks.color = colors.tick;
    chart.options.scales.y.grid.color = colors.grid;
    chart.update();
  }

  private format(value: number): string {
    return `${Math.round(value).toLocaleString('en-US')} ms`;
  }

  private palette() {
    const dark = document.documentElement.getAttribute('data-theme') === 'dark';
    return {
      bars: dark ? '#e8e8e4' : '#1a1a1a',
      label: dark ? '#c9c9c4' : '#555555',
      tick: dark ? '#8d8d88' : '#999999',
      grid: dark ? 'rgba(255, 255, 255, 0.08)' : 'rgba(17, 17, 17, 0.06)',
      tooltipBg: dark ? '#232427' : '#ffffff',
      tooltipText: dark ? '#ededed' : '#111111',
      tooltipBorder: dark ? '#3d3e43' : '#e5e5e5'
    };
  }

  private buildChartData(points: ChartDataPoint[]) {
    const colors = this.palette();
    return {
      labels: points.map(p => p.label),
      datasets: [{
        data: points.map(p => p.value),
        backgroundColor: colors.bars,
        hoverBackgroundColor: colors.bars,
        borderRadius: 4,
        borderSkipped: false,
        barPercentage: 0.62,
        categoryPercentage: 0.8
      }]
    };
  }

  private buildOptions() {
    const colors = this.palette();
    return {
      responsive: true,
      maintainAspectRatio: false,
      layout: { padding: { top: 20 } },
      plugins: {
        legend: { display: false },
        tooltip: {
          backgroundColor: colors.tooltipBg,
          titleColor: colors.tooltipText,
          bodyColor: colors.tooltipText,
          borderColor: colors.tooltipBorder,
          borderWidth: 1,
          padding: 10,
          cornerRadius: 6,
          displayColors: false,
          callbacks: {
            label: (item: { parsed: { y: number | null } }) =>
              this.format(item.parsed.y ?? 0)
          }
        }
      },
      scales: {
        x: {
          grid: { display: false },
          border: { color: colors.grid },
          ticks: { color: colors.tick, font: { size: 11 } }
        },
        y: {
          grid: { color: colors.grid },
          border: { display: false },
          ticks: {
            color: colors.tick,
            font: { size: 11 },
            maxTicksLimit: 5,
            callback: (value: string | number) => Number(value).toLocaleString('en-US')
          },
          beginAtZero: true
        }
      }
    };
  }
}
