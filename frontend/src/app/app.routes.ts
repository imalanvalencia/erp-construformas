import { Routes } from '@angular/router';
import { authGuard } from './auth/guards/auth.guard';
import { adminGuard } from './auth/guards/admin.guard';
import { ShellComponent } from './shell/shell.component';
import { LoginComponent } from './auth/pages/login/login.component';
import { AuthLayoutComponent } from './auth/auth-layout.component';
import { DashboardComponent } from './features/dashboard/dashboard.component';
import { ClientsComponent } from './features/clients/clients.component';
import { ProjectsComponent } from './features/projects/projects.component';
import { BudgetsComponent } from './features/budgets/budgets.component';
import { BudgetEditorPageComponent } from './features/budgets/pages/budget-editor-page/budget-editor-page.component';
import { InvoicesComponent } from './features/invoices/invoices.component';
import { SuppliersComponent } from './features/suppliers/suppliers.component';
import { PaymentsComponent } from './features/payments/payments.component';
import { ClockComponent } from './features/clock/clock.component';
import { ProfileComponent } from './features/profile/profile.component';
import { UsersComponent } from './features/users/users.component';
import { DesignSystem } from './features/design-system/design-system';

export const routes: Routes = [
  {
    path: 'login',
    component: AuthLayoutComponent,
    children: [{ path: '', component: LoginComponent }],
  },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      { path: '', component: DashboardComponent },
      { path: 'clients', component: ClientsComponent },
      { path: 'projects', component: ProjectsComponent },
      { path: 'budgets', component: BudgetsComponent },
      { path: 'budgets/:id/editor', component: BudgetEditorPageComponent },
      { path: 'invoices', component: InvoicesComponent },
      { path: 'ui', component: DesignSystem },
      // { path: 'suppliers', component: SuppliersComponent },
      // { path: 'payments', component: PaymentsComponent },
      { path: 'clock', component: ClockComponent },
      { path: 'profile', component: ProfileComponent },
      { path: 'users', component: UsersComponent, canActivate: [adminGuard] },
    ],
  },
  { path: '**', redirectTo: '' },
];
