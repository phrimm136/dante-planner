import importlib.machinery
import importlib.util
from pathlib import Path

import pytest

CLI_FILE = Path(__file__).resolve().parents[1] / "bin" / "map"
loader = importlib.machinery.SourceFileLoader("map_cli", str(CLI_FILE))
map_cli = importlib.util.module_from_spec(importlib.util.spec_from_loader("map_cli", loader))
loader.exec_module(map_cli)

ROUTER_TEXT = """
const listRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/items',
  component: lazyRoute(() => import('@/pages/items/ListPage')),
})

const detailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/items/$id',
  component: lazyRoute(() => import('@/pages/items/DetailPage')),
})

const aboutRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/about',
  component: lazyRoute(() => import('@/pages/about/AboutPage')),
})
"""

SOURCE_FILES = {
    "lib/router.tsx": ROUTER_TEXT,
    "lib/api.ts": "export const api = 1\n",
    "pages/items/ListPage.tsx": "import { card } from './card'\nimport { api } from '@/lib/api'\n",
    "pages/items/DetailPage.tsx": "import { card } from './card'\nimport { useItem } from './useItem'\n"
                                  "import { api } from '@/lib/api'\nimport './detail.css'\n",
    "pages/items/card.ts": "export const card = 1\n",
    "pages/items/useItem.ts": "import { api } from '@/lib/api'\nexport const useItem = () => api.get(`/api/items/${id}`)\n",
    "pages/items/detail.css": "",
    "pages/items/__tests__/useItem.test.ts": "import { useItem } from '../useItem'\nconst text = \"import x from './gone'\"\n",
    "pages/about/AboutPage.tsx": "import { api } from '@/lib/api'\nconst ping = '/api/ping'\n",
    "pages/orphan.ts": "export const orphan = 1\n",
}

CONTROLLER_TEXT = """
@RestController
@RequestMapping("/api/items")
public class ItemController {
    @GetMapping("/{id}")
    public Item get() { return null; }

    @DeleteMapping("/{id}")
    public void delete() {}
}
"""


@pytest.fixture
def repo_root(tmp_path):
    for relative_path, file_text in SOURCE_FILES.items():
        source_file = tmp_path / "frontend" / "src" / relative_path
        source_file.parent.mkdir(parents=True, exist_ok=True)
        source_file.write_text(file_text)
    controller_file = tmp_path / "backend" / "src" / "main" / "java" / "ItemController.java"
    controller_file.parent.mkdir(parents=True)
    controller_file.write_text(CONTROLLER_TEXT)
    return tmp_path


def run_map(repo_root, capsys, *command_words):
    exit_code = map_cli.main(["--root", str(repo_root), *command_words])
    return exit_code, capsys.readouterr().out.splitlines()


def test_list_counts_own_and_reached_files_per_route(repo_root, capsys):
    exit_code, out_lines = run_map(repo_root, capsys, "list")
    assert exit_code == 0
    assert out_lines == [
        "3 routes",
        "/items      own   1  reach    3  frontend/src/pages/items/ListPage.tsx",
        "/items/$id  own   2  reach    4  frontend/src/pages/items/DetailPage.tsx",
        "/about      own   1  reach    2  frontend/src/pages/about/AboutPage.tsx",
    ]


def test_show_splits_own_narrow_and_names_both_sides_of_an_endpoint(repo_root, capsys, monkeypatch):
    monkeypatch.setattr(map_cli, "NARROW_ROUTES", 2)
    exit_code, out_lines = run_map(repo_root, capsys, "show", "/items/$id")
    assert exit_code == 0
    assert out_lines == [
        "route  /items/$id  frontend/src/lib/router.tsx:10",
        "page   frontend/src/pages/items/DetailPage.tsx",
        "own    2 files reached by this route alone",
        "  frontend/src/pages/items/DetailPage.tsx",
        "  frontend/src/pages/items/useItem.ts",
        "with   /items  (1 files)",
        "  frontend/src/pages/items/card.ts",
        "wider  1 files reached by more than 2 routes; map show '/items/$id' --shared lists them",
        "api    endpoints named by the own and narrowly shared files",
        "  /api/items/:x",
        "    fe  frontend/src/pages/items/useItem.ts",
        "    be  GET    backend/src/main/java/ItemController.java:5",
        "    be  DELETE backend/src/main/java/ItemController.java:8",
        "tests  of the own files",
        "  frontend/src/pages/items/__tests__/useItem.test.ts",
    ]


def test_show_shared_lists_the_wider_files(repo_root, capsys, monkeypatch):
    monkeypatch.setattr(map_cli, "NARROW_ROUTES", 2)
    _, out_lines = run_map(repo_root, capsys, "show", "/items/$id", "--shared")
    wider_at = out_lines.index("wider  1 files reached by more than 2 routes")
    assert out_lines[wider_at + 1] == "  frontend/src/lib/api.ts"


@pytest.mark.parametrize("path_form", ["frontend/src/pages/items/card.ts", "src/pages/items/card.ts", "ABSOLUTE"])
def test_for_names_every_route_that_reaches_the_file(repo_root, capsys, path_form):
    if path_form == "ABSOLUTE":
        path_form = str(repo_root / "frontend/src/pages/items/card.ts")
    assert run_map(repo_root, capsys, "for", path_form) == (0, ["/items", "/items/$id"])


def test_for_a_file_no_route_reaches_says_so(repo_root, capsys):
    _, out_lines = run_map(repo_root, capsys, "for", "src/pages/orphan.ts")
    assert out_lines == ["no route reaches this file; map deps <file> shows what imports it"]


def test_deps_lists_imports_importers_tests_routes_and_endpoints(repo_root, capsys):
    exit_code, out_lines = run_map(repo_root, capsys, "deps", "src/pages/items/useItem.ts")
    assert exit_code == 0
    assert out_lines == [
        "imports  (1)",
        "  frontend/src/lib/api.ts",
        "imported by  (1)",
        "  frontend/src/pages/items/DetailPage.tsx",
        "tests  (1)",
        "  frontend/src/pages/items/__tests__/useItem.test.ts",
        "routes  (1)",
        "  /items/$id",
        "api",
        "  /api/items/:x",
        "    fe  frontend/src/pages/items/useItem.ts",
        "    be  GET    backend/src/main/java/ItemController.java:5",
        "    be  DELETE backend/src/main/java/ItemController.java:8",
    ]


def test_doctor_reports_an_endpoint_no_controller_maps_and_still_passes(repo_root, capsys):
    exit_code, out_lines = run_map(repo_root, capsys, "doctor")
    assert exit_code == 0
    assert out_lines == [
        "routes 3, source files 9, controller endpoints 2",
        "route entries without a page: 0",
        "imports that resolve to no file: 0",
        "frontend endpoints no controller maps: 1",
        "  /api/ping",
    ]


def test_doctor_fails_on_an_import_that_resolves_to_no_file(repo_root, capsys):
    (repo_root / "frontend/src/pages/items/card.ts").write_text("import { gone } from './gone'\n")
    exit_code, out_lines = run_map(repo_root, capsys, "doctor")
    assert exit_code == 1
    assert "  frontend/src/pages/items/card.ts: ./gone" in out_lines


def test_doctor_names_a_route_entry_whose_page_is_missing(repo_root, capsys):
    (repo_root / "frontend/src/pages/about/AboutPage.tsx").unlink()
    _, out_lines = run_map(repo_root, capsys, "doctor")
    assert out_lines[1:3] == ["route entries without a page: 1", "  /about"]


def test_an_unknown_route_names_the_near_ones_and_the_list_command(repo_root):
    with pytest.raises(map_cli.MapError, match=r"no route '/items/x' in router.tsx; near: /items, /items/\$id; run map list"):
        map_cli.main(["--root", str(repo_root), "show", "/items/x"])


def test_a_file_outside_the_frontend_sources_names_the_accepted_forms(repo_root):
    with pytest.raises(map_cli.MapError, match="pass an absolute path, frontend/src/... or src/..."):
        map_cli.main(["--root", str(repo_root), "deps", "backend/src/main/java/ItemController.java"])


def test_an_unreadable_command_names_the_commands(repo_root):
    with pytest.raises(map_cli.MapError, match="the commands are list, show <route>"):
        map_cli.main(["--root", str(repo_root), "show"])


def test_a_root_without_a_router_names_the_fix(tmp_path):
    with pytest.raises(map_cli.MapError, match="pass --root <a LimbusPlanner checkout or worktree>"):
        map_cli.main(["--root", str(tmp_path), "list"])


def test_help_prints_the_usage(repo_root, capsys):
    exit_code, out_lines = run_map(repo_root, capsys, "help")
    assert exit_code == 0 and out_lines[0] == "usage: map [--root <path>] <command>"


def test_no_command_writes_a_file(repo_root, capsys):
    def tree_state():
        return sorted((str(one_path), one_path.stat().st_mtime_ns) for one_path in repo_root.rglob("*"))
    before_state = tree_state()
    for command_words in (["list"], ["show", "/items/$id", "--shared"], ["for", "src/pages/items/card.ts"],
                          ["deps", "src/pages/items/useItem.ts"], ["doctor"], ["help"]):
        map_cli.main(["--root", str(repo_root), *command_words])
    capsys.readouterr()
    assert tree_state() == before_state
