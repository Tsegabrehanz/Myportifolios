import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { EmployeeApiService } from '../../core/services/employee-api.service';
import { Employee } from '../../core/models/employee.model';

interface OrgChartNode {
  employee: Employee;
  children: OrgChartNode[];
}

@Component({
  selector: 'app-org-chart',
  standalone: true,
  imports: [CommonModule, RouterLink, MatCardModule, MatIconModule, MatButtonModule, MatChipsModule, MatProgressSpinnerModule],
  templateUrl: './org-chart.component.html',
  styleUrl: './org-chart.component.scss'
})
export class OrgChartComponent implements OnInit {
  private readonly employeeApi = inject(EmployeeApiService);

  readonly loading = signal(true);
  readonly roots = signal<OrgChartNode[]>([]);
  readonly collapsed = signal<Set<number>>(new Set());
  readonly totalCount = signal(0);

  ngOnInit(): void {
    this.employeeApi.list().subscribe((employees) => {
      this.totalCount.set(employees.length);
      this.roots.set(this.buildTree(employees));
      this.loading.set(false);
    });
  }

  /**
   * Builds a tree from the flat employee list using each employee's
   * existing managerId/managerName - no new backend data needed. A
   * "root" is anyone with no manager, OR whose manager isn't present in
   * this particular result set - the latter matters because the
   * employee list itself is already visibility-scoped server-side (a
   * MANAGER only sees their own direct reports, not the whole company),
   * so their own manager may legitimately be absent from what's
   * returned. Treating that as a root rather than dropping the person
   * entirely keeps the chart complete for whatever data the viewer
   * actually has access to.
   */
  private buildTree(employees: Employee[]): OrgChartNode[] {
    const nodeById = new Map<number, OrgChartNode>();
    for (const employee of employees) {
      nodeById.set(employee.id, { employee, children: [] });
    }

    const roots: OrgChartNode[] = [];
    for (const employee of employees) {
      const node = nodeById.get(employee.id)!;
      const managerNode = employee.managerId !== null ? nodeById.get(employee.managerId) : undefined;
      if (managerNode) {
        managerNode.children.push(node);
      } else {
        roots.push(node);
      }
    }

    const sortByName = (a: OrgChartNode, b: OrgChartNode) =>
      `${a.employee.lastName}${a.employee.firstName}`.localeCompare(`${b.employee.lastName}${b.employee.firstName}`);
    const sortRecursive = (nodes: OrgChartNode[]) => {
      nodes.sort(sortByName);
      nodes.forEach((n) => sortRecursive(n.children));
    };
    sortRecursive(roots);

    return roots;
  }

  isCollapsed(employeeId: number): boolean {
    return this.collapsed().has(employeeId);
  }

  toggleCollapse(employeeId: number): void {
    const next = new Set(this.collapsed());
    if (next.has(employeeId)) {
      next.delete(employeeId);
    } else {
      next.add(employeeId);
    }
    this.collapsed.set(next);
  }
}
