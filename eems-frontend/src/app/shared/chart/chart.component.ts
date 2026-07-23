import {
  AfterViewInit,
  Component,
  ElementRef,
  Input,
  OnChanges,
  OnDestroy,
  SimpleChanges,
  ViewChild
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { Chart, ChartConfiguration, ChartType, registerables } from 'chart.js';

Chart.register(...registerables);

/**
 * Thin wrapper around Chart.js. Kept deliberately generic (type + labels +
 * data + optional colors) rather than one component per chart type, since
 * every chart on the analytics dashboard is a simple single-dataset
 * bar/doughnut/line chart - a dedicated ng2-charts-style dependency wasn't
 * worth the Angular version conflict it introduced (ng2-charts currently
 * requires Angular 21+; this project targets Angular 18).
 *
 * Every chart in the app passes a `description` - a one-line caption
 * explaining what the chart shows and how to read it, rendered below the
 * canvas. This isn't optional decoration: a bare chart with no axis
 * context (e.g. a doughnut with just colored wedges and a legend) is
 * genuinely hard to interpret correctly without it.
 */
@Component({
  selector: 'app-chart',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="canvas-wrapper" [style.height.px]="height">
      <canvas #canvas></canvas>
    </div>
    @if (description) {
      <p class="chart-description" [class.light-text]="lightText">{{ description }}</p>
    }
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .canvas-wrapper {
        position: relative;
        width: 100%;
      }
      canvas {
        width: 100% !important;
        height: 100% !important;
      }
      .chart-description {
        margin: 8px 0 0;
        font-size: 0.75rem;
        color: #666;
        line-height: 1.4;
      }
      .chart-description.light-text {
        color: rgba(255, 255, 255, 0.75);
      }
    `
  ]
})
export class ChartComponent implements AfterViewInit, OnChanges, OnDestroy {
  @Input({ required: true }) type!: ChartType;
  @Input({ required: true }) labels: string[] = [];
  @Input({ required: true }) data: number[] = [];
  @Input() label = '';
  @Input() colors?: string[];
  @Input() height = 260;
  /** Use white-ish axis/legend/description text - for placement on a dark background. */
  @Input() lightText = false;
  /** One-line caption below the chart explaining what it shows and how to read it. Omit only for purely decorative charts. */
  @Input() description?: string;

  @ViewChild('canvas', { static: true }) canvasRef!: ElementRef<HTMLCanvasElement>;

  private chart?: Chart;

  private readonly defaultPalette = [
    '#1565c0', '#42a5f5', '#66bb6a', '#ffa726', '#ef5350', '#ab47bc', '#26a69a', '#8d6e63'
  ];

  ngAfterViewInit(): void {
    this.render();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (this.chart && (changes['data'] || changes['labels'])) {
      this.render();
    }
  }

  ngOnDestroy(): void {
    this.chart?.destroy();
  }

  private render(): void {
    this.chart?.destroy();

    const palette = this.colors ?? this.defaultPalette;
    const isMultiColor = this.type === 'doughnut' || this.type === 'pie';
    const textColor = this.lightText ? 'rgba(255, 255, 255, 0.85)' : undefined;
    const gridColor = this.lightText ? 'rgba(255, 255, 255, 0.15)' : undefined;

    const config: ChartConfiguration = {
      type: this.type,
      data: {
        labels: this.labels,
        datasets: [
          {
            label: this.label,
            data: this.data,
            backgroundColor: isMultiColor
              ? this.labels.map((_, i) => palette[i % palette.length])
              : palette[0],
            borderRadius: this.type === 'bar' ? 4 : 0
          }
        ]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { display: isMultiColor, position: 'bottom', labels: { color: textColor } }
        },
        scales:
          this.type === 'bar'
            ? {
                y: { beginAtZero: true, ticks: { precision: 0, color: textColor }, grid: { color: gridColor } },
                x: { ticks: { color: textColor }, grid: { color: gridColor } }
              }
            : undefined
      }
    };

    this.chart = new Chart(this.canvasRef.nativeElement, config);
  }
}
