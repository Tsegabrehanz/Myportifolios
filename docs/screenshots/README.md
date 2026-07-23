# Screenshots needed here

`PORTFOLIO.md` references these six files by exact name. Capture each
from your own running app (`npm start` + `mvn spring-boot:run`, logged
in as the role noted) and drop them in this folder — PORTFOLIO.md will
pick them up automatically once they exist, no other changes needed.

| Filename | Page | Suggested account |
|---|---|---|
| `01-login.png` | `/login` | (not logged in) |
| `02-dashboard.png` | `/dashboard` | `admin@eems.local` (shows the analytics sidebar) |
| `03-analytics.png` | `/analytics` | `admin@eems.local` or `hr_admin` role |
| `04-employee-profile.png` | `/employees/:id` (any employee) | `admin@eems.local` |
| `05-leave-balances.png` | `/leave-balances` | `admin@eems.local` or HR_ADMIN |
| `06-audit-log.png` | `/audit-logs` | `admin@eems.local` |

## Tips for good screenshots

- **Populate real data first** — import the sample CSVs (departments →
  positions → employees) before capturing, so the screenshots show a
  populated app instead of near-empty seed data. See the root
  `README.md` for the sample data files and import order.
- **Browser window width ~1440px** works well for these two-column
  layouts without cutting anything off.
- **PNG, not JPEG** — screenshots of UI (text, sharp edges) compress
  much better and look cleaner as PNG.
- Crop out your own browser chrome/bookmarks bar if you want a cleaner
  look, though it's not required.

## Also update in `PORTFOLIO.md`

Two placeholders need your actual info before this looks right:
- `<your-github-username>` and `<your-repo-name>` in the GitHub link
  near the top and in the "View source" line.
- The `@<your-github-username>` handle.
