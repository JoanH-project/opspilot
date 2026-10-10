import type { ReactElement } from 'react';

import type { WorkspaceDashboard, WorkspaceSummary } from '../types/workspace';

type DashboardPageProps = {
  dashboard: WorkspaceDashboard;
  workspace: WorkspaceSummary;
};

type Stat = {
  label: string;
  value: number;
};

function formatTimestamp(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date);
}

function StatGroup({ title, stats }: { title: string; stats: Stat[] }): ReactElement {
  return (
    <section className="dashboard-group" aria-label={title}>
      <h2>{title}</h2>
      <div className="stat-grid">
        {stats.map((stat) => (
          <article className="stat-card" key={stat.label}>
            <span>{stat.label}</span>
            <strong>{stat.value}</strong>
          </article>
        ))}
      </div>
    </section>
  );
}

export function DashboardPage({ dashboard, workspace }: DashboardPageProps): ReactElement {
  return (
    <div className="dashboard-page">
      <header className="dashboard-header">
        <p className="eyebrow">Workspace overview</p>
        <h1>{workspace.name}</h1>
        <p>A current snapshot of activity across this workspace.</p>
      </header>

      <div className="dashboard-groups">
        <StatGroup
          title="Projects"
          stats={[
            { label: 'Active', value: dashboard.projects.active },
            { label: 'Archived', value: dashboard.projects.archived },
          ]}
        />
        <StatGroup
          title="Tasks"
          stats={[
            { label: 'Total', value: dashboard.tasks.total },
            { label: 'To do', value: dashboard.tasks.todo },
            { label: 'In progress', value: dashboard.tasks.inProgress },
            { label: 'Done', value: dashboard.tasks.done },
            { label: 'Overdue', value: dashboard.tasks.overdue },
          ]}
        />
        <StatGroup
          title="Documents"
          stats={[
            { label: 'Active', value: dashboard.documents.active },
            { label: 'Archived', value: dashboard.documents.archived },
          ]}
        />
      </div>

      <section className="activity-panel" aria-labelledby="recent-activity-heading">
        <div className="section-heading">
          <div>
            <p className="eyebrow">Workspace feed</p>
            <h2 id="recent-activity-heading">Recent activity</h2>
          </div>
          <span className="activity-limit">Latest {dashboard.recentActivities.length}</span>
        </div>

        {dashboard.recentActivities.length === 0 ? (
          <p className="activity-empty">Workspace changes will appear here.</p>
        ) : (
          <ol className="activity-list">
            {dashboard.recentActivities.map((activity) => (
              <li className="activity-item" key={activity.id}>
                <span className="activity-marker" aria-hidden="true">
                  {activity.entityType.slice(0, 1).toUpperCase()}
                </span>
                <div className="activity-copy">
                  <p>{activity.message}</p>
                  <small>
                    {activity.actor.name} ·{' '}
                    <time dateTime={activity.createdAt}>{formatTimestamp(activity.createdAt)}</time>
                  </small>
                </div>
                <span className="activity-type">{activity.type.replaceAll('_', ' ').toLowerCase()}</span>
              </li>
            ))}
          </ol>
        )}
      </section>
    </div>
  );
}
