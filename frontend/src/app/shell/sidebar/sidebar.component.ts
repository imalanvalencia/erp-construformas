import { Component, inject, output } from '@angular/core';
import { AuthService } from '@auth/services/auth.service';
import { MatIconModule } from '@angular/material/icon';
import { NavLinkComponent } from '@components/sidebar/nav-link/nav-link.component';
import { ButtonComponent } from '@shared-components';

interface NavItem {
  icon: string;
  label: string;
  route: string;
  adminOnly?: boolean;
  /** When present, the item is visible only to these roles (API scope). */
  roles?: string[];
}

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [MatIconModule, ButtonComponent, NavLinkComponent],
  template: `
    <aside class="flex flex-col w-60 h-full bg-inverse-on-surface text-black font-sans select-none">

    <!-- Brand -->
      <section class="flex flex-col items-center gap-3 shrink-0 px-4 py-6">
        <span class="text-xl font-bold text-black tracking-tight">ConstruFormas</span>
        <app-button variant="filled" icon="add"> Nuevo Presupuesto </app-button>
      </section>

      <nav class="flex-1">
        <!-- Navigation -->
        <div class="flex flex-col flex-1 overflow-y-auto py-4 gap-1">
          @for (item of visibleNavItems; track item.route) {
            <app-nav-link route="{{ item.route }}" (click)="toggle.emit()" icon="{{ item.icon }}">
              {{ item.label }}
            </app-nav-link>
          }
        </div>
      </nav>

      <!-- Logout -->
      <div class="border-t border-white/10 px-4 py-6 shrink-0">
        <app-button icon="logout" variant="text" (click)="onLogout()">
          <span>Cerrar sesión</span>
        </app-button>
      </div>
    </aside>
  `,
})
export class SidebarComponent {
  toggle = output<void>();

  private authService = inject(AuthService);
  private allNavItems: NavItem[] = [
    { icon: 'dashboard', label: 'Dashboard', route: '/' },
    { icon: 'groups', label: 'Clientes', route: '/clients' },
    { icon: 'gite', label: 'Proyectos', route: '/projects' },
    { icon: 'assignment', label: 'Presupuestos', route: '/budgets' },
    { icon: 'receipt_long', label: 'Facturas', route: '/invoices' },
    // { icon: 'home', label: 'Proveedores', route: '/suppliers' },
    // { icon: 'home', label: 'Pagos', route: '/payments' },
    { icon: 'schedule', label: 'Fichajes', route: '/clock', roles: ['ADMIN', 'OPERATOR'] },
    { icon: 'user', label: 'Usuarios', route: '/users', adminOnly: true },
  ];

  get visibleNavItems(): NavItem[] {
    // The API returns bare role names ("ADMIN"), not Spring's "ROLE_ADMIN".
    const isAdmin = this.authService.hasRole('ADMIN');
    return this.allNavItems.filter((item) => {
      if (item.roles) return item.roles.some((role) => this.authService.hasRole(role));
      return !item.adminOnly || isAdmin;
    });
  }

  onLogout(): void {
    this.authService.logout();
    window.location.href = '/login';
  }
}
