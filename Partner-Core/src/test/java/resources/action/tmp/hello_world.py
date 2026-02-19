import sys


def parse_args(argv):
    """
    将 --key=value 解析成 dict
    """
    params = {}
    for arg in argv:
        if arg.startswith("--") and "=" in arg:
            key, value = arg[2:].split("=", 1)
            params[key] = value
    return params


def main():
    params = parse_args(sys.argv[1:])

    name = params.get("name", "World")
    print(f"Hello {name}!")


if __name__ == "__main__":
    main()
